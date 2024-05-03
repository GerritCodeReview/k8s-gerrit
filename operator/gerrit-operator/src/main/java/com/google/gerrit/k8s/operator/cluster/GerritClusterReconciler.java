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

package com.google.gerrit.k8s.operator.cluster;

import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_GERRIT_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_INC_REPL_TASK_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CM_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.PVC_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterStatus;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerrit;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritNetwork;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritNetworkCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedIncomingReplicationTask;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedReceiver;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedReceiverCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.NfsIdmapdConfigMap;
import com.google.gerrit.k8s.operator.cluster.dependent.NfsWorkaroundCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.SharedPVC;
import com.google.gerrit.k8s.operator.cluster.dependent.SharedPVCCondition;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "shared-pvc",
          type = SharedPVC.class,
          reconcilePrecondition = SharedPVCCondition.class,
          useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          type = NfsIdmapdConfigMap.class,
          reconcilePrecondition = NfsWorkaroundCondition.class,
          useEventSourceWithName = CM_EVENT_SOURCE),
      @Dependent(
          name = "gerrits",
          type = ClusterManagedGerrit.class,
          reconcilePrecondition = ClusterManagedGerritCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_EVENT_SOURCE),
      @Dependent(
          name = "receiver",
          type = ClusterManagedReceiver.class,
          reconcilePrecondition = ClusterManagedReceiverCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE),
      @Dependent(
          type = ClusterManagedGerritNetwork.class,
          reconcilePrecondition = ClusterManagedGerritNetworkCondition.class,
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE),
      @Dependent(
          type = ClusterManagedIncomingReplicationTask.class,
          useEventSourceWithName = CLUSTER_MANAGED_INC_REPL_TASK_EVENT_SOURCE),
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String CM_EVENT_SOURCE = "cm-event-source";
  public static final String PVC_EVENT_SOURCE = "pvc-event-source";
  public static final String CLUSTER_MANAGED_GERRIT_EVENT_SOURCE = "cluster-managed-gerrit";
  public static final String CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE = "cluster-managed-receiver";
  public static final String CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE =
      "cluster-managed-gerrit-network";
  public static final String CLUSTER_MANAGED_INC_REPL_TASK_EVENT_SOURCE =
      "cluster-managed-incoming-repl-task";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    InformerEventSource<ConfigMap, GerritCluster> cmEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);

    InformerEventSource<PersistentVolumeClaim, GerritCluster> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);

    InformerEventSource<Gerrit, GerritCluster> clusterManagedGerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context).build(), context);

    InformerEventSource<Receiver, GerritCluster> clusterManagedReceiverEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Receiver.class, context).build(), context);

    InformerEventSource<GerritNetwork, GerritCluster> clusterManagedGerritNetworkEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritNetwork.class, context).build(), context);

    InformerEventSource<IncomingReplicationTask, GerritCluster>
        clusterManagedIncomingReplTaskEventSource =
            new InformerEventSource<>(
                InformerConfiguration.from(IncomingReplicationTask.class, context).build(),
                context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(CM_EVENT_SOURCE, cmEventSource);
    eventSources.put(PVC_EVENT_SOURCE, pvcEventSource);
    eventSources.put(CLUSTER_MANAGED_GERRIT_EVENT_SOURCE, clusterManagedGerritEventSource);
    eventSources.put(CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE, clusterManagedReceiverEventSource);
    eventSources.put(
        CLUSTER_MANAGED_GERRIT_NETWORK_EVENT_SOURCE, clusterManagedGerritNetworkEventSource);
    eventSources.put(
        CLUSTER_MANAGED_INC_REPL_TASK_EVENT_SOURCE, clusterManagedIncomingReplTaskEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    List<GerritTemplate> managedGerrits = gerritCluster.getSpec().getGerrits();
    Map<String, List<String>> members = new HashMap<>();
    members.put(
        "gerrit",
        managedGerrits.stream().map(g -> g.getMetadata().getName()).collect(Collectors.toList()));
    ReceiverTemplate managedReceiver = gerritCluster.getSpec().getReceiver();
    if (managedReceiver != null) {
      members.put("receiver", List.of(managedReceiver.getMetadata().getName()));
    }
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
