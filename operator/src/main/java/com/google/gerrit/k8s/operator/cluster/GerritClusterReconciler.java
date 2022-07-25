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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class GerritClusterReconciler implements Reconciler<GerritCluster>, Cleaner<GerritCluster> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String REPOSITORY_PVC_NAME = "git-repositories-pvc";

  private final KubernetesClient kubernetesClient;

  public GerritClusterReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    String ns = gerritCluster.getMetadata().getNamespace();
    String name = gerritCluster.getMetadata().getName();
    log.info("Reconciling Gerrit site with name: {}/{}", ns, name);

    createOrReplaceGitRepositoryStorage(gerritCluster);

    return UpdateControl.updateStatus(updateStatus(gerritCluster));
  }

  private void createOrReplaceGitRepositoryStorage(GerritCluster gerritCluster) {
    checkStorageClass(
        gerritCluster.getSpec().getStorageClasses().getReadWriteOnce(),
        gerritCluster.getSpec().getStorageClasses().getReadWriteMany());

    PersistentVolumeClaim gitRepoPvc =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(REPOSITORY_PVC_NAME)
            .withLabels(
                gerritCluster.getLabels(
                    "git-repositories-storage", this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteMany")
            .withNewResources()
            .withRequests(
                Map.of("storage", gerritCluster.getSpec().getGitRepositoryStorage().getSize()))
            .endResources()
            .withStorageClassName(gerritCluster.getSpec().getStorageClasses().getReadWriteMany())
            .withSelector(gerritCluster.getSpec().getGitRepositoryStorage().getSelector())
            .withVolumeName(gerritCluster.getSpec().getGitRepositoryStorage().getVolumeName())
            .endSpec()
            .build();

    kubernetesClient
        .resource(gitRepoPvc)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .createOrReplace();
  }

  private GerritCluster updateStatus(GerritCluster gerritCluster) {
    GerritClusterStatus status = gerritCluster.getStatus();
    if (kubernetesClient.persistentVolumeClaims().withName(REPOSITORY_PVC_NAME).get() != null) {
      status.setRepositoryPvcName(REPOSITORY_PVC_NAME);
    } else {
      status.setRepositoryPvcName(null);
    }
    gerritCluster.setStatus(status);
    return gerritCluster;
  }

  private void checkStorageClass(String... storageClassNames) {
    List<String> storageClasses =
        kubernetesClient.storage().storageClasses().list().getItems().stream()
            .map(sc -> sc.getMetadata().getName())
            .collect(Collectors.toList());

    for (String sc : storageClassNames) {
      if (!storageClasses.contains(sc)) {
        log.warn("Storageclass {} not found.", sc);
      }
    }
  }

  @Override
  public DeleteControl cleanup(GerritCluster gerritCluster, Context<GerritCluster> context) {
    kubernetesClient
        .persistentVolumeClaims()
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .withName(gerritCluster.getMetadata().getName())
        .delete();
    return DeleteControl.defaultDelete();
  }
}
