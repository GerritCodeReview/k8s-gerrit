// Copyright (C) 2023 The Android Open Source Project
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
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClusterManagedGerrit
    extends CRUDReconcileAddKubernetesDependentResource<Gerrit, GerritCluster>
    implements Deleter<GerritCluster>, BulkDependentResource<Gerrit, GerritCluster> {

  public ClusterManagedGerrit() {
    super(Gerrit.class);
  }

  @Override
  public Map<String, Gerrit> desiredResources(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Map<String, Gerrit> gerrits = new HashMap<>();
    for (GerritTemplate template : gerritCluster.getSpec().getGerrits()) {
      if (gerrits.get(template.getMetadata().getName()) != null) {
        throw new IllegalArgumentException("Each gerrit spec must use a different metadata.name");
      }
      gerrits.put(template.getMetadata().getName(), desired(gerritCluster, template));
    }
    return gerrits;
  }

  private Gerrit desired(GerritCluster gerritCluster, GerritTemplate template) {
    return template.toGerrit(gerritCluster);
  }

  @Override
  public Map<String, Gerrit> getSecondaryResources(
      GerritCluster primary, Context<GerritCluster> context) {
    Set<Gerrit> gerrits = context.getSecondaryResources(Gerrit.class);
    Map<String, Gerrit> result = new HashMap<>(gerrits.size());
    for (Gerrit gerrit : gerrits) {
      result.put(gerrit.getMetadata().getName(), gerrit);
    }
    return result;
  }
}
