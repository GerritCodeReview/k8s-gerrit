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
import com.google.gerrit.k8s.operator.receiver.Receiver;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
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
  private static final String GERRIT_ISTIO_EVENT_SOURCE = "gerrit-istio";

  private final KubernetesClient client;

  private GerritIngress gerritIngress;
  private GerritIstioGateway gerritIstioGateway;

  @Inject
  public GerritClusterReconciler(KubernetesClient client) {
    this.client = client;

    this.gerritIngress = new GerritIngress();
    this.gerritIngress.setKubernetesClient(client);

    this.gerritIstioGateway = new GerritIstioGateway();
    this.gerritIstioGateway.setKubernetesClient(client);
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

    final SecondaryToPrimaryMapper<Receiver> receiverMapper =
        (Receiver receiver) ->
            context
                .getPrimaryCache()
                .list(
                    gerritCluster ->
                        gerritCluster
                            .getMetadata()
                            .getName()
                            .equals(receiver.getSpec().getCluster()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<Receiver, GerritCluster> receiverEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Receiver.class, context)
                .withSecondaryToPrimaryMapper(receiverMapper)
                .build(),
            context);

    InformerEventSource<PersistentVolumeClaim, GerritCluster> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);

    Map<String, EventSource> eventSources =
        EventSourceInitializer.nameEventSources(gerritEventSource, receiverEventSource);
    eventSources.put(PVC_EVENT_SOURCE, pvcEventSource);
    eventSources.put(GERRIT_INGRESS_EVENT_SOURCE, this.gerritIngress.initEventSource(context));
    eventSources.put(GERRIT_ISTIO_EVENT_SOURCE, gerritIstioGateway.initEventSource(context));
    return eventSources;
  }

  @Override
  public UpdateControl<GerritCluster> reconcile(
      GerritCluster gerritCluster, Context<GerritCluster> context) {
    Map<String, List<String>> members = new HashMap<>();
    members.put("gerrit", getManagedMemberInstances(gerritCluster, Gerrit.class));
    members.put("receiver", getManagedMemberInstances(gerritCluster, Receiver.class));
    if (members.values().stream().flatMap(Collection::stream).count() > 0
        && gerritCluster.getSpec().getIngress().isEnabled()) {
      switch (gerritCluster.getSpec().getIngress().getType()) {
        case INGRESS:
          gerritIngress.reconcile(gerritCluster, context);
          break;
        case ISTIO:
          gerritIstioGateway.reconcile(gerritCluster, context);
          break;
        default:
          throw new IllegalStateException("Unknown Ingress type.");
      }
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

  private List<String> getManagedMemberInstances(
      GerritCluster gerritCluster,
      Class<? extends GerritClusterMember<? extends GerritClusterMemberSpec, ?>> clazz) {
    return client
        .resources(clazz)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(c -> GerritCluster.isMemberPartOfCluster(c.getSpec(), gerritCluster))
        .map(c -> c.getMetadata().getName())
        .collect(Collectors.toList());
  }
}
