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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsSimpleLB;
import io.fabric8.istio.api.networking.v1beta1.TrafficPolicyBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GerritIstioDestinationRule
    extends KubernetesDependentResource<DestinationRule, GerritCluster>
    implements Creator<DestinationRule, GerritCluster>,
        Updater<DestinationRule, GerritCluster>,
        Deleter<GerritCluster>,
        BulkDependentResource<DestinationRule, GerritCluster>,
        GarbageCollected<GerritCluster> {

  public GerritIstioDestinationRule() {
    super(DestinationRule.class);
  }

  protected DestinationRule desired(GerritCluster gerritCluster, GerritTemplate gerrit) {

    return new DestinationRuleBuilder()
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels(getName(gerrit), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHost(GerritService.getHostname(gerrit.toGerrit(gerritCluster)))
        .withTrafficPolicy(
            new TrafficPolicyBuilder()
                .withNewLoadBalancer()
                .withNewLoadBalancerSettingsSimpleLbPolicy()
                .withSimple(LoadBalancerSettingsSimpleLB.LEAST_CONN)
                .endLoadBalancerSettingsSimpleLbPolicy()
                .endLoadBalancer()
                .build())
        .endSpec()
        .build();
  }

  public static String getName(GerritTemplate gerrit) {
    return gerrit.getMetadata().getName();
  }

  @Override
  public Map<String, DestinationRule> desiredResources(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Map<String, DestinationRule> drs = new HashMap<>();
    for (GerritTemplate template : gerritCluster.getSpec().getGerrits()) {
      drs.put(template.getMetadata().getName(), desired(gerritCluster, template));
    }
    return drs;
  }

  @Override
  public Map<String, DestinationRule> getSecondaryResources(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Set<DestinationRule> drs = context.getSecondaryResources(DestinationRule.class);
    Map<String, DestinationRule> result = new HashMap<>(drs.size());
    for (DestinationRule dr : drs) {
      result.put(dr.getMetadata().getName(), dr);
    }
    return result;
  }
}
