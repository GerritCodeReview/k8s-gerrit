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

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent
public class NfsIdmapdConfigMap
    extends CRUDReconcileAddKubernetesDependentResource<ConfigMap, GerritCluster> {
  public static final String NFS_IDMAPD_CM_NAME = "nfs-idmapd-config";

  public NfsIdmapdConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(NFS_IDMAPD_CM_NAME)
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(
            GerritClusterLabelFactory.create(
                gerritCluster, NFS_IDMAPD_CM_NAME, this.getClass().getSimpleName()))
        .endMetadata()
        .withData(
            Map.of(
                "idmapd.conf",
                gerritCluster
                    .getSpec()
                    .getStorage()
                    .getStorageClasses()
                    .getNfsWorkaround()
                    .getIdmapdConfig()))
        .build();
  }
}
