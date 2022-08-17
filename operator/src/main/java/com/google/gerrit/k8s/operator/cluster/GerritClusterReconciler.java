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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import java.util.Map;

@ControllerConfiguration(
    dependents = {
      @Dependent(type = GitRepositoriesPVC.class),
      @Dependent(type = GerritLogsPVC.class),
      @Dependent(type = GerritIngress.class)
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  private final KubernetesClient kubernetesClient;

  private NfsIdmapdConfigMap dependentNfsImapdConfigMap;
  private PluginCachePVC dependentPluginCachePvc;

  public GerritClusterReconciler(KubernetesClient client) {
    this.kubernetesClient = client;

    this.dependentNfsImapdConfigMap = new NfsIdmapdConfigMap();
    this.dependentNfsImapdConfigMap.setKubernetesClient(kubernetesClient);

    this.dependentPluginCachePvc = new PluginCachePVC();
    this.dependentPluginCachePvc.setKubernetesClient(kubernetesClient);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    return EventSourceInitializer.nameEventSources(
        this.dependentNfsImapdConfigMap.initEventSource(context),
        this.dependentPluginCachePvc.initEventSource(context));
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

    return UpdateControl.noUpdate();
  }
}
