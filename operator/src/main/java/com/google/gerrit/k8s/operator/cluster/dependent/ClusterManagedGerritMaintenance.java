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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.util.KubernetesDependentCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class ClusterManagedGerritMaintenance
    extends KubernetesDependentCustomResource<GerritMaintenance, GerritCluster> {
  public static final String NAME_SUFFIX = "gerrit-maintenance";

  public ClusterManagedGerritMaintenance() {
    super(GerritMaintenance.class);
  }

  @Override
  public GerritMaintenance desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    GerritMaintenance maintenance = new GerritMaintenance();
    maintenance.setMetadata(
        new ObjectMetaBuilder()
            .withName(String.format("%s-%s", gerritCluster.getMetadata().getName(), NAME_SUFFIX))
            .withNamespace(gerritCluster.getMetadata().getNamespace())
            .build());
    maintenance.setSpec(
        gerritCluster
            .getSpec()
            .getScheduledTasks()
            .getGerritMaintenance()
            .toGerritMaintenanceSpec(gerritCluster));
    return maintenance;
  }
}
