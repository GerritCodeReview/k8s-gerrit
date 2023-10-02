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

package com.google.gerrit.k8s.operator.cluster;

import com.google.gerrit.k8s.operator.v1alpha.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public abstract class GerritClusterMemberDependentResource<
        R extends HasMetadata, P extends GerritClusterMember<? extends GerritClusterMemberSpec, ?>>
    extends CRUDKubernetesDependentResource<R, P> {

  public GerritClusterMemberDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  protected GerritCluster getGerritCluster(P primary) {
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(primary.getMetadata().getNamespace())
            .withName(primary.getSpec().getCluster())
            .get();

    if (gerritCluster == null) {
      throw new IllegalStateException("The Gerrit cluster could not be found.");
    }

    return gerritCluster;
  }
}
