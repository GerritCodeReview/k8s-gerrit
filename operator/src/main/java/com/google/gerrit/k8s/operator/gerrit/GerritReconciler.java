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

package com.google.gerrit.k8s.operator.gerrit;

import static com.google.gerrit.k8s.operator.gerrit.GerritReconciler.CONFIG_MAP_EVENT_SOURCE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritIngressConfig.IngressType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult.Operation;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "gerrit-configmap",
          type = GerritConfigMapDependentResource.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-init-configmap",
          type = GerritInitConfigMapDependentResource.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-statefulset",
          type = StatefulSetDependentResource.class,
          dependsOn = {"gerrit-configmap", "gerrit-init-configmap"}),
      @Dependent(
          name = "gerrit-service",
          type = ServiceDependentResource.class,
          dependsOn = {"gerrit-statefulset"})
    })
public class GerritReconciler implements Reconciler<Gerrit>, EventSourceInitializer<Gerrit> {
  public static final String CONFIG_MAP_EVENT_SOURCE = "configmap-event-source";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SECRET_EVENT_SOURCE_NAME = "secret-event-source";
  private final KubernetesClient client;

  private final GerritIstioVirtualService virtualService;
  private final GerritIstioDestinationRule destinationRule;

  @Inject
  public GerritReconciler(KubernetesClient client) {
    this.client = client;

    this.virtualService = new GerritIstioVirtualService();
    this.virtualService.setKubernetesClient(client);

    this.destinationRule = new GerritIstioDestinationRule();
    this.destinationRule.setKubernetesClient(client);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Gerrit> context) {
    final SecondaryToPrimaryMapper<GerritCluster> gerritClusterMapper =
        (GerritCluster cluster) ->
            context
                .getPrimaryCache()
                .list(
                    gerrit -> gerrit.getSpec().getCluster().equals(cluster.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritCluster, Gerrit> gerritClusterEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritCluster.class, context)
                .withSecondaryToPrimaryMapper(gerritClusterMapper)
                .build(),
            context);

    final SecondaryToPrimaryMapper<Secret> secretMapper =
        (Secret secret) ->
            context
                .getPrimaryCache()
                .list(
                    gerrit ->
                        gerrit.getSpec().getSecrets().contains(secret.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<Secret, Gerrit> secretEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Secret.class, context)
                .withSecondaryToPrimaryMapper(secretMapper)
                .build(),
            context);

    InformerEventSource<ConfigMap, Gerrit> configmapEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);

    Map<String, EventSource> eventSources =
        EventSourceInitializer.nameEventSources(
            gerritClusterEventSource,
            virtualService.initEventSource(context),
            destinationRule.initEventSource(context));
    eventSources.put(CONFIG_MAP_EVENT_SOURCE, configmapEventSource);
    eventSources.put(SECRET_EVENT_SOURCE_NAME, secretEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<Gerrit> reconcile(Gerrit gerrit, Context<Gerrit> context) throws Exception {
    if (gerrit.getStatus() != null && isGerritRestartRequired(gerrit, context)) {
      restartGerritStatefulSet(gerrit);
    }

    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(gerrit.getSpec().getCluster())
            .get();

    if (gerritCluster == null) {
      throw new IllegalStateException("The Gerrit cluster could not be found.");
    }

    if (gerritCluster.getSpec().getIngress().isEnabled()
        && gerritCluster.getSpec().getIngress().getType() == IngressType.ISTIO) {
      this.virtualService.reconcile(gerrit, context);
      this.destinationRule.reconcile(gerrit, context);
    }

    return UpdateControl.patchStatus(updateStatus(gerrit, context));
  }

  void restartGerritStatefulSet(Gerrit gerrit) {
    logger.atInfo().log(
        "Restarting Gerrit %s due to configuration change.", gerrit.getMetadata().getName());
    client
        .apps()
        .statefulSets()
        .inNamespace(gerrit.getMetadata().getNamespace())
        .withName(gerrit.getMetadata().getName())
        .rolling()
        .restart();
  }

  private Gerrit updateStatus(Gerrit gerrit, Context<Gerrit> context) {
    GerritStatus status = gerrit.getStatus();
    if (status == null) {
      status = new GerritStatus();
    }
    Optional<WorkflowReconcileResult> result =
        context.managedDependentResourceContext().getWorkflowReconcileResult();
    if (result.isPresent()) {
      status.setReady(result.get().allDependentResourcesReady());
    } else {
      status.setReady(false);
    }

    Map<String, String> secretVersions = new HashMap<>();
    for (String sec : gerrit.getSpec().getSecrets()) {
      secretVersions.put(
          sec,
          client
              .secrets()
              .inNamespace(gerrit.getMetadata().getNamespace())
              .withName(sec)
              .get()
              .getMetadata()
              .getResourceVersion());
    }
    status.setAppliedSecretVersions(secretVersions);

    gerrit.setStatus(status);
    return gerrit;
  }

  @SuppressWarnings("rawtypes")
  private boolean isGerritRestartRequired(Gerrit gerrit, Context<Gerrit> context) {
    ManagedDependentResourceContext managedResources = context.managedDependentResourceContext();
    Optional<WorkflowReconcileResult> reconcileResults =
        managedResources.getWorkflowReconcileResult();
    if (reconcileResults.isPresent()) {
      Collection<ReconcileResult> results = reconcileResults.get().getReconcileResults().values();
      for (ReconcileResult r : results) {
        if (r.getSingleResource().isPresent()
            && r.getSingleResource().get() instanceof ConfigMap
            && (r.getSingleOperation().equals(Operation.UPDATED)
                || r.getSingleOperation().equals(Operation.CREATED))) {
          return true;
        }
      }
    }

    for (String sec : gerrit.getSpec().getSecrets()) {
      String secVersion =
          client
              .secrets()
              .inNamespace(gerrit.getMetadata().getNamespace())
              .withName(sec)
              .get()
              .getMetadata()
              .getResourceVersion();
      if (!secVersion.equals(gerrit.getStatus().getAppliedSecretVersions().get(sec))) {
        logger.atFine().log(
            "Looking up Secret: %s; Installed secret resource version: %s; Resource version known to Gerrit: %s",
            sec, secVersion, gerrit.getStatus().getAppliedSecretVersions().get(sec));
        return true;
      }
    }
    return false;
  }
}
