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

import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=git-garbage-collection")
public class DependentGitGarbageCollectionList
    extends CRUDKubernetesDependentResource<GitGarbageCollectionList, GerritCluster> {

  public DependentGitGarbageCollectionList() {
    super(GitGarbageCollectionList.class);
  }

  @Override
  protected GitGarbageCollectionList desired(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    GitGarbageCollectionList gitGcList = new GitGarbageCollectionList();
    gitGcList.setMetadata(getGitGarbageCollectionListMetaData(gerritCluster));
    gitGcList.setSpec(gerritCluster.getSpec().getGitGarbageCollections());
    return gitGcList;
  }

  private ObjectMeta getGitGarbageCollectionListMetaData(GerritCluster gerritCluster) {
    return new ObjectMetaBuilder()
        .withName("git-gc-list")
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels("git-gc-list", this.getClass().getSimpleName()))
        .build();
  }
}
