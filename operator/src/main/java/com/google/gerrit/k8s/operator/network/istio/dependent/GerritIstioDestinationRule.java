// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.k8s.operator.network.istio.dependent;

import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritHeadlessService;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.istio.api.networking.v1beta1.ClientTLSSettings;
import io.fabric8.istio.api.networking.v1beta1.ClientTLSSettingsBuilder;
import io.fabric8.istio.api.networking.v1beta1.ClientTLSSettingsTLSmode;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettings;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsSimpleLB;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GerritIstioDestinationRule
    extends CRUDReconcileAddKubernetesDependentResource<DestinationRule, GerritNetwork>
    implements Deleter<GerritNetwork>, BulkDependentResource<DestinationRule, GerritNetwork> {

  public GerritIstioDestinationRule() {
    super(DestinationRule.class);
  }

  private DestinationRule desired(
      GerritNetwork gerritNetwork, String gerritName, boolean isReplica) {

    return new DestinationRuleBuilder()
        .withMetadata(getMetaData(gerritNetwork, gerritName, ""))
        .withNewSpec()
        .withHost(
            new GerritService().getHostname(gerritName, gerritNetwork.getMetadata().getNamespace()))
        .withNewTrafficPolicy()
        .withLoadBalancer(getLoadBalancerSettings(isReplica))
        .endTrafficPolicy()
        .endSpec()
        .build();
  }

  private DestinationRule desiredHeadless(
      GerritNetwork gerritNetwork, String gerritName, boolean isReplica) {

    return new DestinationRuleBuilder()
        .withMetadata(getMetaData(gerritNetwork, gerritName, "-headless-service"))
        .withNewSpec()
        .withHost(
            new GerritHeadlessService()
                .getHostname(gerritName, gerritNetwork.getMetadata().getNamespace()))
        .withNewTrafficPolicy()
        .withLoadBalancer(getLoadBalancerSettings(isReplica))
        .withTls(getTls(gerritNetwork))
        .endTrafficPolicy()
        .endSpec()
        .build();
  }

  private DestinationRule desiredHeadlessPod(GerritNetwork gerritNetwork, String gerritName) {
    return new DestinationRuleBuilder()
        .withMetadata(getMetaData(gerritNetwork, gerritName, "-headless-pods"))
        .withNewSpec()
        .withHost(
            "*."
                + new GerritHeadlessService()
                    .getHostname(gerritName, gerritNetwork.getMetadata().getNamespace()))
        .withNewTrafficPolicy()
        .withTls(null)
        .withTls(getTls(gerritNetwork))
        .endTrafficPolicy()
        .endSpec()
        .build();
  }

  private ObjectMeta getMetaData(
      GerritNetwork gerritNetwork, String gerritName, String nameSuffix) {
    return new ObjectMetaBuilder()
        .withName(getName(gerritName, nameSuffix))
        .withNamespace(gerritNetwork.getMetadata().getNamespace())
        .withLabels(
            GerritClusterLabelFactory.create(
                gerritNetwork.getMetadata().getName(),
                getName(gerritName, nameSuffix),
                this.getClass().getSimpleName()))
        .build();
  }

  private LoadBalancerSettings getLoadBalancerSettings(boolean isReplica) {
    if (isReplica) {
      return new LoadBalancerSettingsBuilder()
          .withNewLoadBalancerSettingsSimpleLbPolicy()
          .withSimple(LoadBalancerSettingsSimpleLB.LEAST_CONN)
          .endLoadBalancerSettingsSimpleLbPolicy()
          .build();
    }
    return new LoadBalancerSettingsBuilder()
        .withNewLoadBalancerSettingsConsistentHashLbPolicy()
        .withNewConsistentHash()
        .withNewLoadBalancerSettingsConsistentHashLBUseSourceIpKey(true)
        .endConsistentHash()
        .endLoadBalancerSettingsConsistentHashLbPolicy()
        .build();
  }

  private ClientTLSSettings getTls(GerritNetwork gerritNetwork) {
    return new ClientTLSSettingsBuilder()
        .withMode(
            gerritNetwork.getSpec().getIngress().getIstio().ismTLS()
                ? ClientTLSSettingsTLSmode.ISTIO_MUTUAL
                : ClientTLSSettingsTLSmode.DISABLE)
        .build();
  }

  public static String getName(GerritTemplate gerrit, String suffix) {
    return getName(gerrit.getMetadata().getName(), suffix);
  }

  public static String getName(String gerritName, String suffix) {
    return gerritName + suffix;
  }

  @Override
  public Map<String, DestinationRule> desiredResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Map<String, DestinationRule> drs = new HashMap<>();
    if (gerritNetwork.hasPrimaryGerrit()) {
      String primaryGerritName = gerritNetwork.getSpec().getPrimaryGerrit().getName();
      DestinationRule dstRule = desired(gerritNetwork, primaryGerritName, false);
      drs.put(dstRule.getMetadata().getName(), dstRule);
      DestinationRule dstRuleHeadless = desiredHeadless(gerritNetwork, primaryGerritName, false);
      drs.put(dstRuleHeadless.getMetadata().getName(), dstRuleHeadless);
      DestinationRule dstRuleHeadlessPod = desiredHeadlessPod(gerritNetwork, primaryGerritName);
      drs.put(dstRuleHeadlessPod.getMetadata().getName(), dstRuleHeadlessPod);
    }
    if (gerritNetwork.hasGerritReplica()) {
      String gerritReplicaName = gerritNetwork.getSpec().getGerritReplica().getName();
      DestinationRule dstRule = desired(gerritNetwork, gerritReplicaName, true);
      drs.put(dstRule.getMetadata().getName(), dstRule);
      DestinationRule dstRuleHeadless = desiredHeadless(gerritNetwork, gerritReplicaName, true);
      drs.put(dstRuleHeadless.getMetadata().getName(), dstRuleHeadless);
      DestinationRule dstRuleHeadlessPod = desiredHeadlessPod(gerritNetwork, gerritReplicaName);
      drs.put(dstRuleHeadlessPod.getMetadata().getName(), dstRuleHeadlessPod);
    }
    return drs;
  }

  @Override
  public Map<String, DestinationRule> getSecondaryResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Set<DestinationRule> drs = context.getSecondaryResources(DestinationRule.class);
    Map<String, DestinationRule> result = new HashMap<>(drs.size());
    for (DestinationRule dr : drs) {
      result.put(dr.getMetadata().getName(), dr);
    }
    return result;
  }
}
