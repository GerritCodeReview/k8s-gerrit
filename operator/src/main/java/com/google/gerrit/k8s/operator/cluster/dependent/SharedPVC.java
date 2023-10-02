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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.util.CRUDKubernetesDependentPVCResource;
import com.google.gerrit.k8s.operator.v1alpha.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1alpha.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.v1alpha.api.model.shared.SharedStorage;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = SharedPVCDiscriminator.class)
public class SharedPVC extends CRUDKubernetesDependentPVCResource<GerritCluster> {

  public static final String SHARED_PVC_NAME = "shared-pvc";

  @Override
  protected PersistentVolumeClaim desiredPVC(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    GerritStorageConfig storageConfig = gerritCluster.getSpec().getStorage();
    SharedStorage sharedStorage = storageConfig.getSharedStorage();
    return new PersistentVolumeClaimBuilder()
        .withNewMetadata()
        .withName(SHARED_PVC_NAME)
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels("shared-storage", this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withAccessModes("ReadWriteMany")
        .withNewResources()
        .withRequests(Map.of("storage", sharedStorage.getSize()))
        .endResources()
        .withStorageClassName(storageConfig.getStorageClasses().getReadWriteMany())
        .withSelector(sharedStorage.getSelector())
        .withVolumeName(sharedStorage.getVolumeName())
        .endSpec()
        .build();
  }
}
