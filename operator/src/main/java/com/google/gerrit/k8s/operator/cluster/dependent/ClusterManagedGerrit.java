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

import com.google.gerrit.k8s.operator.v1beta1.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplate;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClusterManagedGerrit extends KubernetesDependentResource<Gerrit, GerritCluster>
    implements Creator<Gerrit, GerritCluster>,
        Updater<Gerrit, GerritCluster>,
        Deleter<GerritCluster>,
        BulkDependentResource<Gerrit, GerritCluster>,
        GarbageCollected<GerritCluster> {

  public ClusterManagedGerrit() {
    super(Gerrit.class);
  }

  @Override
  public Map<String, Gerrit> desiredResources(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Map<String, Gerrit> gerrits = new HashMap<>();
    for (GerritTemplate template : gerritCluster.getSpec().getGerrits()) {
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
