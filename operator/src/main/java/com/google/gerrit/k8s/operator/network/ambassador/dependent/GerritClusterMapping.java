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

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.api.model.network.NetworkMemberWithSsh;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.MappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class GerritClusterMapping extends AbstractAmbassadorDependentResource<Mapping>
    implements MappingDependentResourceInterface {

  public static final String GERRIT_MAPPING = "gerrit-mapping";

  public GerritClusterMapping() {
    super(Mapping.class);
  }

  @Override
  public Mapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    // If only one Gerrit instance in GerritCluster, send all git-over-https requests to it
    NetworkMemberWithSsh gerrit =
        gerritNetwork.hasGerritReplica()
            ? gerritNetwork.getSpec().getGerritReplica()
            : gerritNetwork.getSpec().getPrimaryGerrit();
    String serviceName = gerrit.getName() + ":" + gerrit.getHttpPort();
    Mapping mapping =
        new MappingBuilder()
            .withNewMetadataLike(
                getCommonMetadata(gerritNetwork, GERRIT_MAPPING, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpecLike(getCommonSpec(gerritNetwork, serviceName))
            .endSpec()
            .build();
    return mapping;
  }
}
