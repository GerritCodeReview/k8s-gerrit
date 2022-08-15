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

package com.google.gerrit.k8s.operator.gitgc;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Map;

@ControllerConfiguration
public class GitGarbageCollectionListReconciler implements Reconciler<GitGarbageCollectionList> {
  private final KubernetesClient kubernetesClient;

  public GitGarbageCollectionListReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public UpdateControl<GitGarbageCollectionList> reconcile(
      GitGarbageCollectionList gitGcList, Context<GitGarbageCollectionList> context) {
    for (GitGarbageCollectionTemplate gitGcTemplate : gitGcList.getSpec().getTemplates()) {
      GitGarbageCollection gitGc = new GitGarbageCollection();
      gitGc.setMetadata(
          getGitGarbageCollectionMetaData(gitGcTemplate.getMetadata().getLabels(), gitGcTemplate));
      gitGc.setSpec(gitGcTemplate.getSpec());
      kubernetesClient.resource(gitGc).createOrReplace();
    }

    return UpdateControl.noUpdate();
  }

  private ObjectMeta getGitGarbageCollectionMetaData(
      Map<String, String> parentLabels, GitGarbageCollectionTemplate gitGcTemplate) {
    ObjectMeta meta = gitGcTemplate.getMetadata();
    Map<String, String> labels = gitGcTemplate.getMetadata().getLabels();
    labels.putAll(parentLabels);
    labels.put("app.kubernetes.io/component", "git-gc");
    labels.put("app.kubernetes.io/created-by", this.getClass().getSimpleName());
    meta.setLabels(labels);
    return meta;
  }
}
