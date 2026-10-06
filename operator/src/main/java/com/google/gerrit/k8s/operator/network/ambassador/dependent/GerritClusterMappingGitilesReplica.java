// Copyright (C) 2026 The Android Open Source Project
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
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.MappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritClusterMappingGitilesReplicaDiscriminator.class)
public class GerritClusterMappingGitilesReplica extends AbstractAmbassadorDependentResource<Mapping>
    implements MappingDependentResourceInterface {

  public static final String GERRIT_MAPPING_GITILES_REPLICA = "gerrit-mapping-gitiles-replica";

  public GerritClusterMappingGitilesReplica() {
    super(Mapping.class);
  }

  @Override
  public Mapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String namespace = gerritNetwork.getMetadata().getNamespace();
    String replicaServiceName =
        new GerritService()
                .getHostname(gerritNetwork.getSpec().getGerritReplica().getName(), namespace)
            + ":"
            + gerritNetwork.getSpec().getGerritReplica().getHttpPort();

    String pathPrefix = gerritNetwork.getSpec().getIngress().getPathPrefix();
    String gitilesPrefix =
        (pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix) + "/plugins/gitiles/";

    // Send Gitiles (read-only repo browsing) requests to the Replica
    Mapping mapping =
        new MappingBuilder()
            .withNewMetadataLike(
                getCommonMetadata(
                    gerritNetwork, GERRIT_MAPPING_GITILES_REPLICA, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpecLike(getCommonSpec(gerritNetwork, replicaServiceName))
            .withPrefix(gitilesPrefix)
            .endSpec()
            .build();

    return mapping;
  }
}
