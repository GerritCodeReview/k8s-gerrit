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
import com.google.gerrit.k8s.operator.util.KubernetesDependentCustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClusterManagedGerrit extends KubernetesDependentCustomResource<Gerrit, GerritCluster>
    implements Deleter<GerritCluster>, BulkDependentResource<Gerrit, GerritCluster, ResourceID> {

  public ClusterManagedGerrit() {
    super(Gerrit.class);
  }

  @Override
  public Map<ResourceID, Gerrit> desiredResources(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Map<ResourceID, Gerrit> gerrits = new HashMap<>();
    for (GerritTemplate template : gerritCluster.getSpec().getGerrits()) {
      Gerrit desired = desired(gerritCluster, template);
      ResourceID id = ResourceID.fromResource(desired);
      if (gerrits.get(id) != null) {
        throw new IllegalArgumentException("Each gerrit spec must use a different metadata.name");
      }
      gerrits.put(id, desired);
    }
    return gerrits;
  }

  private Gerrit desired(GerritCluster gerritCluster, GerritTemplate template) {
    return template.toGerrit(gerritCluster);
  }

  @Override
  public Map<ResourceID, Gerrit> getSecondaryResources(
      GerritCluster primary, Context<GerritCluster> context) {
    Set<Gerrit> gerrits = context.getSecondaryResources(Gerrit.class);
    Map<ResourceID, Gerrit> result = new HashMap<>(gerrits.size());
    for (Gerrit gerrit : gerrits) {
      result.put(ResourceID.fromResource(gerrit), gerrit);
    }
    return result;
  }
}
