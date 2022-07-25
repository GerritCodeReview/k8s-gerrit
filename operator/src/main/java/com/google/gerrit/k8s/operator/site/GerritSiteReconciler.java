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

package com.google.gerrit.k8s.operator.site;

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
public class GerritSiteReconciler implements Reconciler<GerritSite>, Cleaner<GerritSite> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String REPOSITORY_PVC_NAME = "git-repositories-pvc";

  private final KubernetesClient kubernetesClient;

  public GerritSiteReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public UpdateControl<GerritSite> reconcile(GerritSite gerritSite, Context<GerritSite> context) {
    String ns = gerritSite.getMetadata().getNamespace();
    String name = gerritSite.getMetadata().getName();
    log.info("Reconciling Gerrit site with name: {}/{}", ns, name);

    createOrReplaceGitRepositoryStorage(gerritSite);

    return UpdateControl.updateStatus(updateStatus(gerritSite));
  }

  private void createOrReplaceGitRepositoryStorage(GerritSite gerritSite) {
    checkStorageClass(
        gerritSite.getSpec().getStorageClasses().getReadWriteOnce(),
        gerritSite.getSpec().getStorageClasses().getReadWriteMany());

    PersistentVolumeClaim gitRepoPvc =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(REPOSITORY_PVC_NAME)
            .withLabels(
                gerritSite.getLabels("git-repositories-storage", this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteMany")
            .withNewResources()
            .withRequests(
                Map.of("storage", gerritSite.getSpec().getGitRepositoryStorage().getSize()))
            .endResources()
            .withStorageClassName(gerritSite.getSpec().getStorageClasses().getReadWriteMany())
            .withSelector(gerritSite.getSpec().getGitRepositoryStorage().getSelector())
            .withVolumeName(gerritSite.getSpec().getGitRepositoryStorage().getVolumeName())
            .endSpec()
            .build();

    kubernetesClient
        .resource(gitRepoPvc)
        .inNamespace(gerritSite.getMetadata().getNamespace())
        .createOrReplace();
  }

  private GerritSite updateStatus(GerritSite site) {
    GerritSiteStatus status = site.getStatus();
    if (kubernetesClient.persistentVolumeClaims().withName(REPOSITORY_PVC_NAME).get() != null) {
      status.setRepositoryPvcName(REPOSITORY_PVC_NAME);
    } else {
      status.setRepositoryPvcName(null);
    }
    site.setStatus(status);
    return site;
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
  public DeleteControl cleanup(GerritSite gerritSite, Context<GerritSite> context) {
    kubernetesClient
        .persistentVolumeClaims()
        .inNamespace(gerritSite.getMetadata().getNamespace())
        .withName(gerritSite.getMetadata().getName())
        .delete();
    return DeleteControl.defaultDelete();
  }
}
