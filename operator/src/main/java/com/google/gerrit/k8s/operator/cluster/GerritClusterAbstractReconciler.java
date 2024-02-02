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

import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_GERRIT_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterStatus;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerrit;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritCondition;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "gerrits",
          type = ClusterManagedGerrit.class,
          reconcilePrecondition = ClusterManagedGerritCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_EVENT_SOURCE),
    })
public abstract class GerritClusterAbstractReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String CLUSTER_MANAGED_GERRIT_EVENT_SOURCE = "cluster-managed-gerrit";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    InformerEventSource<Gerrit, GerritCluster> clusterManagedGerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(CLUSTER_MANAGED_GERRIT_EVENT_SOURCE, clusterManagedGerritEventSource);
    return eventSources;
  }

  protected GerritCluster updateStatus(
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
