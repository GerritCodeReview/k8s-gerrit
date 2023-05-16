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

import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
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
import java.util.Map;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "git-repositories-pvc",
          type = GitRepositoriesPVC.class,
          useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-logs-pvc",
          type = GerritLogsPVC.class,
          useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          name = "nfs-idmapd-cm",
          type = NfsIdmapdConfigMap.class,
          reconcilePrecondition = NfsWorkaroundCondition.class),
      @Dependent(
          name = "plugin-cache-pvc",
          type = PluginCachePVC.class,
          reconcilePrecondition = PluginCacheCondition.class,
          useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          name = "gerrits",
          type = GerritDependentResource.class,
          dependsOn = {"git-repositories-pvc", "gerrit-logs-pvc"}),
      @Dependent(
          name = "receiver",
          type = ReceiverDependentResource.class,
          dependsOn = {"git-repositories-pvc", "gerrit-logs-pvc"}),
      @Dependent(
          name = "gerrit-ingress",
          type = GerritIngress.class,
          reconcilePrecondition = GerritIngressCondition.class,
          dependsOn = {"gerrits"}),
      @Dependent(
          name = "gerrit-istio-gateway",
          type = GerritIstioGateway.class,
          reconcilePrecondition = GerritIstioCondition.class,
          dependsOn = {"gerrits"}),
      @Dependent(
          name = "gerrit-istio-virtual-service",
          type = GerritIstioVirtualService.class,
          reconcilePrecondition = GerritIstioCondition.class,
          useEventSourceWithName =
              GerritClusterReconciler.GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE,
          dependsOn = {"gerrit-istio-gateway"}),
      @Dependent(
          name = "gerrit-istio-virtual-service-ssh",
          type = GerritIstioVirtualServiceSSH.class,
          reconcilePrecondition = GerritIstioSSHCondition.class,
          useEventSourceWithName =
              GerritClusterReconciler.GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE,
          dependsOn = {"gerrit-istio-gateway"}),
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String PVC_EVENT_SOURCE = "pvc-event-source";
  public static final String GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE =
      "gerrit-istio-virtual-service";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    InformerEventSource<PersistentVolumeClaim, GerritCluster> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);

    InformerEventSource<VirtualService, GerritCluster> virtualServiceEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(VirtualService.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(PVC_EVENT_SOURCE, pvcEventSource);
    eventSources.put(GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE, virtualServiceEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    return UpdateControl.noUpdate();
  }
}
