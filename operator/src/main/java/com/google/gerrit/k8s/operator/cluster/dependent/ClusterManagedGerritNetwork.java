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
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.gerrit.k8s.operator.network.model.GerritNetworkSpec;
import com.google.gerrit.k8s.operator.network.model.NetworkMember;
import com.google.gerrit.k8s.operator.network.model.NetworkMemberWithSsh;
import com.google.gerrit.k8s.operator.receiver.model.ReceiverTemplate;
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
      NetworkMemberWithSsh primaryGerritNetworkMember = new NetworkMemberWithSsh();
      primaryGerritNetworkMember.setName(primaryGerrit.getMetadata().getName());
      primaryGerritNetworkMember.setHttpPort(primaryGerrit.getSpec().getService().getHttpPort());
      primaryGerritNetworkMember.setSshPort(primaryGerrit.getSpec().getService().getSshPort());
      gerritNetworkSpec.setPrimaryGerrit(primaryGerritNetworkMember);
    }

    Optional<GerritTemplate> optionalGerritReplica =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.REPLICA))
            .findFirst();
    if (optionalGerritReplica.isPresent()) {
      GerritTemplate gerritReplica = optionalGerritReplica.get();
      NetworkMemberWithSsh gerritReplicaNetworkMember = new NetworkMemberWithSsh();
      gerritReplicaNetworkMember.setName(gerritReplica.getMetadata().getName());
      gerritReplicaNetworkMember.setHttpPort(gerritReplica.getSpec().getService().getHttpPort());
      gerritReplicaNetworkMember.setSshPort(gerritReplica.getSpec().getService().getSshPort());
      gerritNetworkSpec.setGerritReplica(gerritReplicaNetworkMember);
    }

    ReceiverTemplate receiver = gerritCluster.getSpec().getReceiver();
    if (receiver != null) {
      NetworkMember receiverNetworkMember = new NetworkMember();
      receiverNetworkMember.setName(receiver.getMetadata().getName());
      receiverNetworkMember.setHttpPort(receiver.getSpec().getService().getHttpPort());
      gerritNetworkSpec.setReceiver(receiverNetworkMember);
    }
    gerritNetworkSpec.setIngress(gerritCluster.getSpec().getIngress());
    gerritNetwork.setSpec(gerritNetworkSpec);
    return gerritNetwork;
  }
}
