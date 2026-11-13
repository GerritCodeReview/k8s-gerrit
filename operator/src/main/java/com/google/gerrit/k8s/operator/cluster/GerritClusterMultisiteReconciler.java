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

package com.google.gerrit.k8s.operator.cluster;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterStatus;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerrit;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritNetwork;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritNetworkCondition;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Workflow(
    dependents = {
      @Dependent(
          name = "gerrits",
          type = ClusterManagedGerrit.class,
          reconcilePrecondition = ClusterManagedGerritCondition.class),
      @Dependent(
          type = ClusterManagedGerritNetwork.class,
          reconcilePrecondition = ClusterManagedGerritNetworkCondition.class)
    })
public class GerritClusterMultisiteReconciler implements Reconciler<GerritCluster> {

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    List<GerritTemplate> managedGerrits = gerritCluster.getSpec().getGerrits();
    Map<String, List<String>> members = new HashMap<>();
    members.put(
        "gerrit",
        managedGerrits.stream().map(g -> g.getMetadata().getName()).collect(Collectors.toList()));
    return UpdateControl.patchStatus(updateStatus(gerritCluster, members));
  }

  private GerritCluster updateStatus(
      GerritCluster gerritCluster, Map<String, List<String>> members) {
    GerritClusterStatus status = gerritCluster.getStatus();
    if (status == null) {
      status = new GerritClusterStatus();
    }
    status.setMembers(members);
    gerritCluster.setStatus(status);
    return gerritCluster;
  }
}
