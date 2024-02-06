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
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
<<<<<<< PATCH SET (a0a60f Add Istio traffic management to the Gerrit multi-site setup)
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.dependent.*;
=======
>>>>>>> BASE      (27b4de Expand gerrit-init to set up pull replication)
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
<<<<<<< PATCH SET (a0a60f Add Istio traffic management to the Gerrit multi-site setup)
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "gerrits",
          type = ClusterManagedGerrit.class,
          reconcilePrecondition = ClusterManagedGerritCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_EVENT_SOURCE),
      @Dependent(
          type = ClusterManagedGerritNetwork.class,
          reconcilePrecondition = ClusterManagedGerritNetworkCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE)
    })
public class GerritClusterMultisiteReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String CM_EVENT_SOURCE = "cm-event-source";
  public static final String CLUSTER_MANAGED_GERRIT_EVENT_SOURCE = "cluster-managed-gerrit";

  public static final String CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE =
      "cluster-managed-gerrit-network";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    InformerEventSource<ConfigMap, GerritCluster> cmEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);

    InformerEventSource<Gerrit, GerritCluster> clusterManagedGerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context).build(), context);

    InformerEventSource<GerritNetwork, GerritCluster> clusterManagedGerritNetworkEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritNetwork.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(CM_EVENT_SOURCE, cmEventSource);
    eventSources.put(CLUSTER_MANAGED_GERRIT_EVENT_SOURCE, clusterManagedGerritEventSource);
    eventSources.put(
        CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE, clusterManagedGerritNetworkEventSource);
    return eventSources;
  }
=======
public class GerritClusterMultisiteReconciler extends GerritClusterAbstractReconciler {
>>>>>>> BASE      (27b4de Expand gerrit-init to set up pull replication)

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
}
