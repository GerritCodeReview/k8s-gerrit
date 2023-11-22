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

import com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet;
import com.google.gerrit.k8s.operator.v1beta1.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1beta1.api.model.network.GerritNetwork;
import io.fabric8.istio.api.security.v1beta1.AuthorizationPolicy;
import io.fabric8.istio.api.security.v1beta1.AuthorizationPolicyAction;
import io.fabric8.istio.api.security.v1beta1.AuthorizationPolicyBuilder;
import io.fabric8.istio.api.security.v1beta1.RuleBuilder;
import io.fabric8.istio.api.security.v1beta1.RuleToBuilder;
import io.fabric8.istio.api.type.v1beta1.WorkloadSelectorBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.List;

public class GerritIstioHAAuthorizationPolicy
    extends CRUDKubernetesDependentResource<AuthorizationPolicy, GerritNetwork> {

  public GerritIstioHAAuthorizationPolicy() {
    super(AuthorizationPolicy.class);
  }

  protected AuthorizationPolicy desired(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String gerritName = gerritNetwork.getSpec().getPrimaryGerrit().getName();

    return new AuthorizationPolicyBuilder()
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
        .withSelector(
            new WorkloadSelectorBuilder()
                .withMatchLabels(GerritStatefulSet.getSelectorLabels(gerritName))
                .build())
        .withAction(AuthorizationPolicyAction.DENY)
        .withRules(
            List.of(
                new RuleBuilder()
                    .withTo(
                        List.of(
                            new RuleToBuilder()
                                .withNewOperation()
                                .withPaths(
                                    "/a/plugins/high-availability/*",
                                    "/plugins/high-availability/*")
                                .endOperation()
                                .build()))
                    .build()))
        .endSpec()
        .build();
  }

  public static String getName(GerritTemplate gerrit) {
    return gerrit.getMetadata().getName() + "-ha-authorization-policy";
  }

  public static String getName(String gerritName) {
    return gerritName + "-ha-authorization-policy";
  }
}
