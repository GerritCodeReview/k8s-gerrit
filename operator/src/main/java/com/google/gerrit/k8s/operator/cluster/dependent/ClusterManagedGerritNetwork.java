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

import com.google.gerrit.k8s.operator.v1beta2.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta2.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1beta2.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.v1beta2.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.v1beta2.api.model.network.GerritNetworkSpec;
import com.google.gerrit.k8s.operator.v1beta2.api.model.network.NetworkMember;
import com.google.gerrit.k8s.operator.v1beta2.api.model.network.NetworkMemberWithSsh;
import com.google.gerrit.k8s.operator.v1beta2.api.model.receiver.ReceiverTemplate;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Optional;

@KubernetesDependent
public class ClusterManagedGerritNetwork
    extends CRUDKubernetesDependentResource<GerritNetwork, GerritCluster> {
  public static final String NAME_SUFFIX = "gerrit-network";

  public ClusterManagedGerritNetwork() {
    super(GerritNetwork.class);
  }

  @Override
  public GerritNetwork desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    GerritNetwork gerritNetwork = new GerritNetwork();
    gerritNetwork.setMetadata(
        new ObjectMetaBuilder()
            .withName(gerritCluster.getDependentResourceName(NAME_SUFFIX))
            .withNamespace(gerritCluster.getMetadata().getNamespace())
            .build());
    GerritNetworkSpec gerritNetworkSpec = new GerritNetworkSpec();

    Optional<GerritTemplate> optionalPrimaryGerrit =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.PRIMARY))
            .findFirst();
    if (optionalPrimaryGerrit.isPresent()) {
      GerritTemplate primaryGerrit = optionalPrimaryGerrit.get();
      gerritNetworkSpec.setPrimaryGerrit(
          new NetworkMemberWithSsh(
              primaryGerrit.getMetadata().getName(), primaryGerrit.getSpec().getService()));
    }

    Optional<GerritTemplate> optionalGerritReplica =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.REPLICA))
            .findFirst();
    if (optionalGerritReplica.isPresent()) {
      GerritTemplate gerritReplica = optionalGerritReplica.get();
      gerritNetworkSpec.setGerritReplica(
          new NetworkMemberWithSsh(
              gerritReplica.getMetadata().getName(), gerritReplica.getSpec().getService()));
    }

    ReceiverTemplate receiver = gerritCluster.getSpec().getReceiver();
    if (receiver != null) {
      gerritNetworkSpec.setReceiver(
          new NetworkMember(receiver.getMetadata().getName(), receiver.getSpec().getService()));
    }
    gerritNetworkSpec.setIngress(gerritCluster.getSpec().getIngress());
    gerritNetwork.setSpec(gerritNetworkSpec);
    return gerritNetwork;
  }
}
