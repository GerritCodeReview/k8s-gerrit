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

import com.google.gerrit.k8s.operator.gerrit.Gerrit;
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
      @Dependent(type = GitRepositoriesPVC.class),
      @Dependent(type = GerritLogsPVC.class)
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  private final KubernetesClient kubernetesClient;

  private NfsIdmapdConfigMap dependentNfsImapdConfigMap;
  private PluginCachePVC dependentPluginCachePvc;
  private GerritIngress gerritIngress;

  public GerritClusterReconciler(KubernetesClient client) {
    this.kubernetesClient = client;

    this.dependentNfsImapdConfigMap = new NfsIdmapdConfigMap();
    this.dependentNfsImapdConfigMap.setKubernetesClient(kubernetesClient);

    this.dependentPluginCachePvc = new PluginCachePVC();
    this.dependentPluginCachePvc.setKubernetesClient(kubernetesClient);

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

    return EventSourceInitializer.nameEventSources(
        gerritEventSource,
        this.dependentNfsImapdConfigMap.initEventSource(context),
        this.dependentPluginCachePvc.initEventSource(context),
        this.gerritIngress.initEventSource(context));
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    if (gerritCluster.getSpec().getStorageClasses().getNfsWorkaround().isEnabled()
        && gerritCluster.getSpec().getStorageClasses().getNfsWorkaround().getIdmapdConfig()
            != null) {
      dependentNfsImapdConfigMap.reconcile(gerritCluster, context);
    }

    if (gerritCluster.getSpec().getPluginCacheStorage().isEnabled()) {
      dependentPluginCachePvc.reconcile(gerritCluster, context);
    }

    List<String> managedGerrits = getManagedGerritInstances(gerritCluster);
    if (!managedGerrits.isEmpty() && gerritCluster.getSpec().getIngress().isEnabled()) {
      this.gerritIngress.reconcile(gerritCluster, context);
    }
    return UpdateControl.patchStatus(updateStatus(gerritCluster, managedGerrits));
  }

  private GerritCluster updateStatus(GerritCluster gerritCluster, List<String> managedGerrits) {
    if (managedGerrits.isEmpty()) {
      return gerritCluster;
    }

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
