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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING_RECEIVER;

import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.MappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritClusterMappingReceiverDiscriminator.class)
public class GerritClusterMappingReceiver
    extends CRUDKubernetesDependentResource<Mapping, GerritNetwork> {

  public GerritClusterMappingReceiver() {
    super(Mapping.class);
  }

  @Override
  protected Mapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String receiverServiceName = 
      ReceiverService.getName(gerritNetwork.getSpec().getReceiver().getName()) + 
        gerritNetwork.getSpec().getReceiver().getHttpPort();

    Mapping mapping =
        new MappingBuilder()
            .withNewMetadataLike(
                Utils.getCommonMetadata(
                    gerritNetwork, GERRIT_MAPPING_RECEIVER, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpecLike(Utils.getCommonSpec(gerritNetwork, receiverServiceName))
            .withPrefix("/a/projects|/new|/git")
            .withPrefixRegex(true)
            .endSpec()
            .build();
    return mapping;
  }
}
