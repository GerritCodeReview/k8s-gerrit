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

import static com.google.gerrit.k8s.operator.api.model.network.GerritNetwork.SESSION_COOKIE_NAME;
import static com.google.gerrit.k8s.operator.api.model.network.GerritNetwork.SESSION_COOKIE_TTL;

import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsSimpleLB;
import io.fabric8.istio.api.networking.v1beta1.TrafficPolicy;
import io.fabric8.istio.api.networking.v1beta1.TrafficPolicyBuilder;
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

  protected DestinationRule desired(
      GerritNetwork gerritNetwork, String gerritName, boolean isReplica) {

    return new DestinationRuleBuilder()
        .withNewMetadata()
        .withName(getName(gerritName))
        .withNamespace(gerritNetwork.getMetadata().getNamespace())
        .withLabels(
            GerritCluster.getLabels(
                gerritNetwork.getMetadata().getName(),
                getName(gerritName),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHost(GerritService.getHostname(gerritName, gerritNetwork.getMetadata().getNamespace()))
        .withTrafficPolicy(getTrafficPolicy(isReplica))
        .endSpec()
        .build();
  }

  private TrafficPolicy getTrafficPolicy(boolean isReplica) {
    if (!OperatorContext.isSharedFS()) {
      return new TrafficPolicyBuilder()
          .withNewLoadBalancer()
          .withNewLoadBalancerSettingsConsistentHashLbPolicy()
          .withNewConsistentHash()
          .withNewLoadBalancerSettingsConsistentHashLBUseSourceIpKey()
          .withUseSourceIp(true)
          .endLoadBalancerSettingsConsistentHashLBUseSourceIpKey()
          .endConsistentHash()
          .endLoadBalancerSettingsConsistentHashLbPolicy()
          .endLoadBalancer()
          .build();
    }
    if (isReplica) {
      return new TrafficPolicyBuilder()
          .withNewLoadBalancer()
          .withNewLoadBalancerSettingsSimpleLbPolicy()
          .withSimple(LoadBalancerSettingsSimpleLB.LEAST_CONN)
          .endLoadBalancerSettingsSimpleLbPolicy()
          .endLoadBalancer()
          .build();
    }
    return new TrafficPolicyBuilder()
        .withNewLoadBalancer()
        .withNewLoadBalancerSettingsConsistentHashLbPolicy()
        .withNewConsistentHash()
        .withNewLoadBalancerSettingsConsistentHashLBHttpCookieKey()
        .withNewHttpCookie()
        .withName(SESSION_COOKIE_NAME)
        .withTtl(SESSION_COOKIE_TTL)
        .endHttpCookie()
        .endLoadBalancerSettingsConsistentHashLBHttpCookieKey()
        .endConsistentHash()
        .endLoadBalancerSettingsConsistentHashLbPolicy()
        .endLoadBalancer()
        .build();
  }

  public static String getName(GerritTemplate gerrit) {
    return gerrit.getMetadata().getName();
  }

  public static String getName(String gerritName) {
    return gerritName;
  }

  @Override
  public Map<String, DestinationRule> desiredResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Map<String, DestinationRule> drs = new HashMap<>();
    if (gerritNetwork.hasPrimaryGerrit()) {
      String primaryGerritName = gerritNetwork.getSpec().getPrimaryGerrit().getName();
      drs.put(primaryGerritName, desired(gerritNetwork, primaryGerritName, false));
    }
    if (gerritNetwork.hasGerritReplica()) {
      String gerritReplicaName = gerritNetwork.getSpec().getGerritReplica().getName();
      drs.put(gerritReplicaName, desired(gerritNetwork, gerritReplicaName, true));
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
