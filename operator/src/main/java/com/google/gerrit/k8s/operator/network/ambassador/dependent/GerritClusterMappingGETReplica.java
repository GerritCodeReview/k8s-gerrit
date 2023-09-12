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

import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.MappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;

@KubernetesDependent
public class GerritClusterMappingGETReplica
    extends CRUDKubernetesDependentResource<Mapping, GerritNetwork> {

  private static final int SVCPORT = 8080;

  public GerritClusterMappingGETReplica() {
    super(Mapping.class);
  }

  @Override
  protected Mapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String replicaServiceName =
        gerritNetwork.getSpec().getGerritReplica().getName() + ":" + SVCPORT;

    // Send fetch/clone GET requests to replica
    Mapping mapping =
        new MappingBuilder()
            .withNewMetadataLike(
                Utils.getCommonMetadata(
                    gerritNetwork, "gerrit-mapping-get-replica", this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpecLike(Utils.getCommonSpec(gerritNetwork, replicaServiceName))
            .withNewV2QueryParameters()
            .withAdditionalProperties(
                new HashMap<String, Object>() {
                  {
                    put("service", "git-upload-pack");
                  }
                })
            .endV2QueryParameters()
            .endSpec()
            .build();

    return mapping;
  }
}
