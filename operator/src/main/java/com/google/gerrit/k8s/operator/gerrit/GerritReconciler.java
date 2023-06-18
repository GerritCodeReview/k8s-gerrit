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
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritInitConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritSecret;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritStatus;
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
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(name = "gerrit-secret", type = GerritSecret.class),
      @Dependent(
          name = "gerrit-configmap",
          type = GerritConfigMap.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-init-configmap",
          type = GerritInitConfigMap.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-statefulset",
          type = GerritStatefulSet.class,
          dependsOn = {"gerrit-configmap", "gerrit-init-configmap"}),
      @Dependent(
          name = "gerrit-service",
          type = GerritService.class,
          dependsOn = {"gerrit-statefulset"})
    })
public class GerritReconciler implements Reconciler<Gerrit>, EventSourceInitializer<Gerrit> {
  public static final String CONFIG_MAP_EVENT_SOURCE = "configmap-event-source";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KubernetesClient client;

  @Inject
  public GerritReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Gerrit> context) {
    InformerEventSource<ConfigMap, Gerrit> configmapEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(CONFIG_MAP_EVENT_SOURCE, configmapEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<Gerrit> reconcile(Gerrit gerrit, Context<Gerrit> context) throws Exception {
    if (gerrit.getStatus() != null && isGerritRestartRequired(gerrit, context)) {
      restartGerritStatefulSet(gerrit);
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

    Map<String, String> cmVersions = new HashMap<>();

    cmVersions.put(
        GerritConfigMap.getName(gerrit),
        client
            .configMaps()
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(GerritConfigMap.getName(gerrit))
            .get()
            .getMetadata()
            .getResourceVersion());

    cmVersions.put(
        GerritInitConfigMap.getName(gerrit),
        client
            .configMaps()
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(GerritInitConfigMap.getName(gerrit))
            .get()
            .getMetadata()
            .getResourceVersion());

    status.setAppliedConfigMapVersions(cmVersions);

    Map<String, String> secretVersions = new HashMap<>();
    Optional<Secret> gerritSecret = context.getSecondaryResource(Secret.class);
    if (gerritSecret.isPresent()) {
      secretVersions.put(
          gerrit.getSpec().getSecretRef(), gerritSecret.get().getMetadata().getResourceVersion());
    }
    status.setAppliedSecretVersions(secretVersions);

    gerrit.setStatus(status);
    return gerrit;
  }

  private boolean isGerritRestartRequired(Gerrit gerrit, Context<Gerrit> context) {
    String gerritConfigMapName = GerritConfigMap.getName(gerrit);
    String gerritConfigMapVersion =
        client
            .configMaps()
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(gerritConfigMapName)
            .get()
            .getMetadata()
            .getResourceVersion();
    if (!gerritConfigMapVersion.equals(
        gerrit.getStatus().getAppliedConfigMapVersions().get(gerritConfigMapName))) {
      logger.atInfo().log(
          "Looking up ConfigMap: %s; Installed configmap resource version: %s; Resource version known to Gerrit: %s",
          gerritConfigMapName,
          gerritConfigMapVersion,
          gerrit.getStatus().getAppliedConfigMapVersions().get(gerritConfigMapName));
      return true;
    }

    String gerritInitConfigMapName = GerritInitConfigMap.getName(gerrit);
    String gerritInitConfigMapVersion =
        client
            .configMaps()
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(gerritInitConfigMapName)
            .get()
            .getMetadata()
            .getResourceVersion();
    if (!gerritInitConfigMapVersion.equals(
        gerrit.getStatus().getAppliedConfigMapVersions().get(gerritInitConfigMapName))) {
      logger.atInfo().log(
          "Looking up ConfigMap: %s; Installed configmap resource version: %s; Resource version known to Gerrit: %s",
          gerritInitConfigMapName,
          gerritInitConfigMapVersion,
          gerrit.getStatus().getAppliedConfigMapVersions().get(gerritInitConfigMapName));
      return true;
    }

    String secretName = gerrit.getSpec().getSecretRef();
    Optional<Secret> gerritSecret = context.getSecondaryResource(Secret.class);
    if (gerritSecret.isPresent()) {
      String secVersion = gerritSecret.get().getMetadata().getResourceVersion();
      if (!secVersion.equals(gerrit.getStatus().getAppliedSecretVersions().get(secretName))) {
        logger.atFine().log(
            "Looking up Secret: %s; Installed secret resource version: %s; Resource version known to Gerrit: %s",
            secretName, secVersion, gerrit.getStatus().getAppliedSecretVersions().get(secretName));
        return true;
      }
    }
    return false;
  }
}
