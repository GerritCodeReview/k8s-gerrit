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

import static com.google.gerrit.k8s.operator.cluster.GitRepositoriesPVC.REPOSITORY_PVC_NAME;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(
    dependents = {
      @Dependent(type = GitRepositoriesPVC.class),
    })
public class GerritClusterReconciler implements Reconciler<GerritCluster> {
  private final KubernetesClient kubernetesClient;

  public GerritClusterReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    return UpdateControl.patchStatus(updateStatus(gerritCluster));
  }

  private GerritCluster updateStatus(GerritCluster gerritCluster) {
    GerritClusterStatus status;
    if (gerritCluster.getStatus() == null) {
      status = new GerritClusterStatus();
    } else {
      status = gerritCluster.getStatus();
    }

    if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(gerritCluster.getMetadata().getNamespace())
            .withName(REPOSITORY_PVC_NAME)
            .get()
        != null) {
      status.setRepositoryPvcName(REPOSITORY_PVC_NAME);
    } else {
      status.setRepositoryPvcName(null);
    }
    gerritCluster.setStatus(status);
    return gerritCluster;
  }
}
