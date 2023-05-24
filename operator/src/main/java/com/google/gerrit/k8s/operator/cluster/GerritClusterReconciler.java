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
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.GERRIT_ISTIO_DESTINATION_RULE_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.PVC_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerrit;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedGerritCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedReceiver;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedReceiverCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIngress;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIstioCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIstioDestinationRule;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIstioGateway;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIstioVirtualService;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritIstioVirtualServiceSSH;
import com.google.gerrit.k8s.operator.cluster.dependent.GerritLogsPVC;
import com.google.gerrit.k8s.operator.cluster.dependent.GitRepositoriesPVC;
import com.google.gerrit.k8s.operator.cluster.dependent.NfsIdmapdConfigMap;
import com.google.gerrit.k8s.operator.cluster.dependent.NfsWorkaroundCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.PluginCacheCondition;
import com.google.gerrit.k8s.operator.cluster.dependent.PluginCachePVC;
import com.google.gerrit.k8s.operator.cluster.dependent.ReceiverIstioVirtualService;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.model.GerritClusterStatus;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.receiver.model.Receiver;
import com.google.gerrit.k8s.operator.receiver.model.ReceiverTemplate;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
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
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
          type = NfsIdmapdConfigMap.class,
          reconcilePrecondition = NfsWorkaroundCondition.class),
      @Dependent(
          type = PluginCachePVC.class,
          reconcilePrecondition = PluginCacheCondition.class,
          useEventSourceWithName = PVC_EVENT_SOURCE),
      @Dependent(
          name = "gerrits",
          type = ClusterManagedGerrit.class,
          reconcilePrecondition = ClusterManagedGerritCondition.class,
          dependsOn = {"git-repositories-pvc", "gerrit-logs-pvc"},
          useEventSourceWithName = CLUSTER_MANAGED_GERRIT_EVENT_SOURCE),
      @Dependent(
          name = "receiver",
          type = ClusterManagedReceiver.class,
          reconcilePrecondition = ClusterManagedReceiverCondition.class,
          dependsOn = {"git-repositories-pvc", "gerrit-logs-pvc"},
          useEventSourceWithName = CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-destination-rules",
          type = GerritIstioDestinationRule.class,
          reconcilePrecondition = GerritIstioCondition.class,
          useEventSourceWithName = GERRIT_ISTIO_DESTINATION_RULE_EVENT_SOURCE),
    })
public class GerritClusterReconciler
    implements Reconciler<GerritCluster>, EventSourceInitializer<GerritCluster> {
  public static final String PVC_EVENT_SOURCE = "pvc-event-source";
  public static final String CLUSTER_MANAGED_GERRIT_EVENT_SOURCE = "cluster-managed-gerrit";
  public static final String CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE = "cluster-managed-receiver";
  public static final String GERRIT_ISTIO_DESTINATION_RULE_EVENT_SOURCE =
      "gerrit-istio-destination-rule";
  private static final String GERRIT_INGRESS_EVENT_SOURCE = "gerrit-ingress";
  private static final String GERRIT_ISTIO_GATEWAY_EVENT_SOURCE = "gerrit-istio-gateway";
  private static final String GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE =
      "gerrit-istio-virtual-service";

  private GerritIngress gerritIngress;
  private GerritIstioGateway gerritIstioGateway;
  private GerritIstioVirtualService gerritVirtualService;
  private GerritIstioVirtualServiceSSH gerritVirtualServiceSSH;
  private ReceiverIstioVirtualService receiverVirtualService;

  @Inject
  public GerritClusterReconciler(KubernetesClient client) {
    this.gerritIngress = new GerritIngress();
    this.gerritIngress.setKubernetesClient(client);

    this.gerritIstioGateway = new GerritIstioGateway();
    this.gerritIstioGateway.setKubernetesClient(client);

    this.gerritVirtualService = new GerritIstioVirtualService();
    this.gerritVirtualService.setKubernetesClient(client);

    this.gerritVirtualServiceSSH = new GerritIstioVirtualServiceSSH();
    this.gerritVirtualServiceSSH.setKubernetesClient(client);

    this.receiverVirtualService = new ReceiverIstioVirtualService();
    this.receiverVirtualService.setKubernetesClient(client);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritCluster> context) {
    InformerEventSource<PersistentVolumeClaim, GerritCluster> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);

    InformerEventSource<VirtualService, GerritCluster> virtualServiceEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(VirtualService.class, context).build(), context);

    InformerEventSource<Gerrit, GerritCluster> clusterManagedGerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context).build(), context);

    InformerEventSource<Receiver, GerritCluster> clusterManagedReceiverEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Receiver.class, context).build(), context);

    InformerEventSource<DestinationRule, GerritCluster> gerritIstioDestinationRuleEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(DestinationRule.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(PVC_EVENT_SOURCE, pvcEventSource);
    eventSources.put(CLUSTER_MANAGED_GERRIT_EVENT_SOURCE, clusterManagedGerritEventSource);
    eventSources.put(CLUSTER_MANAGED_RECEIVER_EVENT_SOURCE, clusterManagedReceiverEventSource);
    eventSources.put(
        GERRIT_ISTIO_DESTINATION_RULE_EVENT_SOURCE, gerritIstioDestinationRuleEventSource);
    eventSources.put(GERRIT_INGRESS_EVENT_SOURCE, gerritIngress.initEventSource(context));
    eventSources.put(
        GERRIT_ISTIO_GATEWAY_EVENT_SOURCE, gerritIstioGateway.initEventSource(context));
    gerritVirtualService.configureWith(virtualServiceEventSource);
    gerritVirtualServiceSSH.configureWith(virtualServiceEventSource);
    receiverVirtualService.configureWith(virtualServiceEventSource);
    eventSources.put(GERRIT_ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE, virtualServiceEventSource);
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
    if (members.values().stream().flatMap(Collection::stream).count() > 0
        && gerritCluster.getSpec().getIngress().isEnabled()) {
      switch (gerritCluster.getSpec().getIngress().getType()) {
        case INGRESS:
          gerritIngress.reconcile(gerritCluster, context);
          break;
        case ISTIO:
          gerritIstioGateway.reconcile(gerritCluster, context);

          gerritVirtualService.setResourceDiscriminator(
              new ResourceNameDiscriminator<>(GerritIstioVirtualService.getName(gerritCluster)));
          gerritVirtualServiceSSH.setResourceDiscriminator(
              new ResourceNameDiscriminator<>(GerritIstioVirtualServiceSSH.getName(gerritCluster)));
          receiverVirtualService.setResourceDiscriminator(
              new ResourceNameDiscriminator<>(ReceiverIstioVirtualService.getName(gerritCluster)));

          gerritVirtualService.reconcile(gerritCluster, context);

          if (managedGerrits.stream().anyMatch(g -> g.getSpec().getService().isSshEnabled())) {
            gerritVirtualServiceSSH.reconcile(gerritCluster, context);
          } else {
            gerritVirtualServiceSSH.delete(gerritCluster, context);
          }
          if (managedReceiver != null) {
            receiverVirtualService.reconcile(gerritCluster, context);
          }
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
}
