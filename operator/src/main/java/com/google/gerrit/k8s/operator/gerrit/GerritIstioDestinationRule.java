// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.gerrit;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberDependentResource;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsSimpleLB;
import io.fabric8.istio.api.networking.v1beta1.TrafficPolicyBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class GerritIstioDestinationRule
    extends GerritClusterMemberDependentResource<DestinationRule, Gerrit> {

  public GerritIstioDestinationRule() {
    super(DestinationRule.class);
  }

  @Override
  protected DestinationRule desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster = getGerritCluster(gerrit);

    return new DestinationRuleBuilder()
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels(getName(gerrit), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHost(ServiceDependentResource.getHostname(gerrit))
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

  public static String getName(Gerrit gerrit) {
    return gerrit.getMetadata().getName();
  }
}
