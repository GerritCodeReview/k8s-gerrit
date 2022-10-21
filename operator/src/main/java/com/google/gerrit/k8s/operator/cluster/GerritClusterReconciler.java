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

import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.PVC_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerConfiguration(
    dependents = {
      @Dependent(type = GitRepositoriesPVC.class, useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(type = GerritLogsPVC.class, useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          type = NfsIdmapdConfigMap.class,
          reconcilePrecondition = NfsWorkaroundCondition.class),
      @Dependent(
          type = PluginCachePVC.class,
          reconcilePrecondition = PluginCacheCondition.class,
          useEventSourceWithName = PVC_EVENT_SOURCE)
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String PVC_EVENT_SOURCE = "pvc-event-source";
  private static final String GERRIT_INGRESS_EVENT_SOURCE = "gerrit-ingress";

  private final KubernetesClient kubernetesClient;

  private GerritIngress gerritIngress;

  public GerritClusterReconciler(KubernetesClient client) {
    this.kubernetesClient = client;

    this.gerritIngress = new GerritIngress();
    this.gerritIngress.setKubernetesClient(kubernetesClient);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    final SecondaryToPrimaryMapper<Gerrit> gerritMapper =
        (Gerrit gerrit) ->
            context
                .getPrimaryCache()
                .list(
                    gerritCluster ->
                        gerritCluster.getMetadata().getName().equals(gerrit.getSpec().getCluster()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<Gerrit, GerritCluster> gerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context)
                .withSecondaryToPrimaryMapper(gerritMapper)
                .build(),
            context);

    InformerEventSource<PersistentVolumeClaim, GerritCluster> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);

    Map<String, EventSource> eventSources =
        EventSourceInitializer.nameEventSources(gerritEventSource);
    eventSources.put(PVC_EVENT_SOURCE, pvcEventSource);
    eventSources.put(GERRIT_INGRESS_EVENT_SOURCE, this.gerritIngress.initEventSource(context));

    return eventSources;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    List<String> managedGerrits = getManagedGerritInstances(gerritCluster);
    if (!managedGerrits.isEmpty() && gerritCluster.getSpec().getIngress().isEnabled()) {
      this.gerritIngress.reconcile(gerritCluster, context);
    }
    return UpdateControl.patchStatus(updateStatus(gerritCluster, managedGerrits));
  }

  private GerritCluster updateStatus(GerritCluster gerritCluster, List<String> managedGerrits) {
    GerritClusterStatus status = gerritCluster.getStatus();
    if (status == null) {
      status = new GerritClusterStatus();
    }
    status.setManagedGerritInstances(managedGerrits);
    gerritCluster.setStatus(status);
    return gerritCluster;
  }

  private List<String> getManagedGerritInstances(GerritCluster gerritCluster) {
    return kubernetesClient
        .resources(Gerrit.class)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(gerrit -> GerritCluster.isGerritInstancePartOfCluster(gerrit, gerritCluster))
        .map(gerrit -> gerrit.getMetadata().getName())
        .collect(Collectors.toList());
  }
}
