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
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Map;

public class GerritRepositoriesSnapshotPVC
    extends CRUDReconcileAddKubernetesDependentResource<PersistentVolumeClaim, GerritIndexer> {

  public GerritRepositoriesSnapshotPVC() {
    super(PersistentVolumeClaim.class);
  }

  @Override
  protected PersistentVolumeClaim desired(
      GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    KubernetesClient client = context.getClient();
    GerritCluster gerritCluster = getGerritCluster(gerritIndexer, client);
    return new PersistentVolumeClaimBuilder()
        .withNewMetadata()
        .withName(getName(gerritIndexer))
        .withNamespace(gerritIndexer.getMetadata().getNamespace())
        .withLabels(getLabels(gerritIndexer))
        .endMetadata()
        .withNewSpec()
        .withVolumeMode("Filesystem")
        .withAccessModes("ReadWriteMany")
        .withNewResources()
        .withRequests(
            Map.of("storage", gerritCluster.getSpec().getStorage().getSharedStorage().getSize()))
        .endResources()
        .withNewDataSource()
        .withApiGroup("snapshot.storage.k8s.io")
        .withKind("VolumeSnapshot")
        .withName(GerritRepositoriesVolumeSnapshot.getName(gerritIndexer))
        .endDataSource()
        .endSpec()
        .build();
  }

  public static String getName(GerritIndexer gerritIndexer) {
    return getName(gerritIndexer.getMetadata().getName());
  }

  public static String getName(String gerritIndexerName) {
    return gerritIndexerName + "-repositories-snapshot-pvc";
  }

  private static String getComponentName(String gerritIndexerName) {
    return String.format("gerrit-indexer-%s", gerritIndexerName);
  }

  private static Map<String, String> getLabels(GerritIndexer gerritIndexer) {
    String name = gerritIndexer.getMetadata().getName();
    return GerritCluster.getLabels(
        name, getComponentName(name), GerritIndexerReconciler.class.getSimpleName());
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
