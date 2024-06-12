// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.indexer.dependent;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.cluster.dependent.SharedPVC;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshot;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshotBuilder;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshotClass;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.List;
import java.util.stream.Collectors;

public class GerritRepositoriesVolumeSnapshot
    extends CRUDReconcileAddKubernetesDependentResource<VolumeSnapshot, GerritIndexer> {

  public GerritRepositoriesVolumeSnapshot() {
    super(VolumeSnapshot.class);
  }

  @Override
  protected VolumeSnapshot desired(GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    KubernetesClient client = context.getClient();
    GerritCluster gerritCluster = getGerritCluster(gerritIndexer, client);
    return new VolumeSnapshotBuilder()
        .withNewMetadata()
        .withName(getName(gerritIndexer))
        .withNamespace(gerritIndexer.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
        .withNewSource()
        .withPersistentVolumeClaimName(
            getRepositoriesPVC(gerritIndexer, gerritCluster, client).getMetadata().getName())
        .endSource()
        .withVolumeSnapshotClassName(
            getVolumeSnapshotClass(gerritIndexer, gerritCluster, client).getMetadata().getName())
        .endSpec()
        .build();
  }

  public static String getName(GerritIndexer gerritIndexer) {
    return getName(gerritIndexer.getMetadata().getName());
  }

  public static String getName(String gerritIndexerName) {
    return gerritIndexerName + "-repositories-snapshot";
  }

  private PersistentVolumeClaim getRepositoriesPVC(
      GerritIndexer gerritIndexer, GerritCluster gerritCluster, KubernetesClient client) {
    String ns = gerritCluster.getMetadata().getNamespace();
    return client
        .resources(PersistentVolumeClaim.class)
        .inNamespace(ns)
        .withName(SharedPVC.SHARED_PVC_NAME)
        .get();
  }

  private VolumeSnapshotClass getVolumeSnapshotClass(
      GerritIndexer gerritIndexer, GerritCluster gerritCluster, KubernetesClient client) {
    StorageClass storageClass =
        client
            .resources(StorageClass.class)
            .withName(gerritCluster.getSpec().getStorage().getStorageClasses().getReadWriteMany())
            .get();

    String driver = storageClass.getProvisioner();
    List<VolumeSnapshotClass> volumeSnapshotClasses =
        client.resources(VolumeSnapshotClass.class).list().getItems().stream()
            .filter(vsc -> vsc.getDriver().equals(driver))
            .collect(Collectors.toList());
    if (volumeSnapshotClasses.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "No VolumeSnapshotClass available for StorageClass %s",
              storageClass.getMetadata().getName()));
    } else if (volumeSnapshotClasses.size() > 1) {
      throw new IllegalStateException(
          String.format(
              "Multiple VolumeSnapshotClasses for CSI driver %s. This is not yet supported.",
              driver));
    }
    return volumeSnapshotClasses.get(0);
  }

  private GerritCluster getGerritCluster(GerritIndexer gerritIndexer, KubernetesClient client) {
    String ns = gerritIndexer.getMetadata().getNamespace();
    return client
        .resources(GerritCluster.class)
        .inNamespace(ns)
        .withName(gerritIndexer.getSpec().getCluster())
        .get();
  }
}
