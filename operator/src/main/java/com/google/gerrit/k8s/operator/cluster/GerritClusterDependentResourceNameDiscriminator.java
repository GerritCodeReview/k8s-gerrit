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

package com.google.gerrit.k8s.operator.cluster;

import com.google.gerrit.k8s.operator.v1alpha.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import java.util.Optional;

public class GerritClusterDependentResourceNameDiscriminator<R extends HasMetadata>
    implements ResourceDiscriminator<R, GerritCluster> {

  private final String nameSuffix;

  public GerritClusterDependentResourceNameDiscriminator(String nameSuffix) {
    this.nameSuffix = nameSuffix;
  }

  @Override
  public Optional<R> distinguish(
      Class<R> resource, GerritCluster gerritCluster, Context<GerritCluster> context) {

    return context.getSecondaryResources(resource).stream()
        .filter(
            v ->
                v.getMetadata().getNamespace().equals(gerritCluster.getMetadata().getNamespace())
                    && v.getMetadata()
                        .getName()
                        .equals(
                            String.format(
                                "%s-%s", gerritCluster.getMetadata().getName(), nameSuffix)))
        .findFirst();
  }
}
