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
import static com.google.gerrit.k8s.operator.gerrit.GerritReconciler.GERRIT_SERVICE_EVENT_SOURCE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritStatus;
import com.google.gerrit.k8s.operator.gerrit.dependent.FluentBitConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritHeadlessService;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritInitConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
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
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "fluentbit-configmap",
          type = FluentBitConfigMap.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-configmap",
          type = GerritConfigMap.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-init-configmap",
          type = GerritInitConfigMap.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-service",
          type = GerritService.class,
          useEventSourceWithName = GERRIT_SERVICE_EVENT_SOURCE,
          dependsOn = {"gerrit-statefulset"}),
      @Dependent(
          name = "gerrit-service-headless",
          type = GerritHeadlessService.class,
          useEventSourceWithName = GERRIT_SERVICE_EVENT_SOURCE,
          dependsOn = {"gerrit-statefulset"})
    })
public class GerritReconciler implements Reconciler<Gerrit>, EventSourceInitializer<Gerrit> {
  public static final String GERRIT_SECRET_EVENT_SOURCE = "gerrit-secret-event-source";
  public static final String CONFIG_MAP_EVENT_SOURCE = "configmap-event-source";
  public static final String GERRIT_SERVICE_EVENT_SOURCE = "gerrit-service-event-source";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KubernetesClient client;

  @Inject
  public GerritReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Gerrit> context) {
    Map<String, EventSource> eventSources = new HashMap<>();

    InformerEventSource<ConfigMap, Gerrit> configmapEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);
    eventSources.put(CONFIG_MAP_EVENT_SOURCE, configmapEventSource);

    InformerEventSource<Service, Gerrit> gerritServiceEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Service.class, context).build(), context);
    eventSources.put(GERRIT_SERVICE_EVENT_SOURCE, gerritServiceEventSource);

    SecondaryToPrimaryMapper<Secret> secretMapper = new SecretToGerritMapper(context);
    InformerEventSource<Secret, Gerrit> moduleMetaDataEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Secret.class, context)
                .withSecondaryToPrimaryMapper(secretMapper)
                .build(),
            context);
    eventSources.put(GERRIT_SECRET_EVENT_SOURCE, moduleMetaDataEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<Gerrit> reconcile(Gerrit gerrit, Context<Gerrit> context) throws Exception {
    return UpdateControl.patchStatus(updateStatus(gerrit, context));
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

    addConfigMapsStatus(gerrit, status);
    addSecretsStatus(gerrit, context, status);

    gerrit.setStatus(status);
    return gerrit;
  }

  public void addConfigMapsStatus(Gerrit gerrit, GerritStatus status) {
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

    logger.atFine().log("Adding ConfigMap versions: %s", cmVersions);
    status.setAppliedConfigMapVersions(cmVersions);
  }

  public void addSecretsStatus(Gerrit gerrit, Context<Gerrit> context, GerritStatus status) {
    // GerritSecret
    Map<String, String> secretVersions = new HashMap<>();
    String gerritSecretName = gerrit.getSpec().getSecretRef();
    if (gerritSecretName != null && !gerritSecretName.isBlank()) {
      Optional<Secret> gerritSecret =
          Optional.ofNullable(
              client
                  .secrets()
                  .inNamespace(gerrit.getMetadata().getNamespace())
                  .withName(gerritSecretName)
                  .get());
      if (gerritSecret.isPresent()) {
        secretVersions.put(gerritSecretName, gerritSecret.get().getMetadata().getResourceVersion());
      }
    }

    // GerritModuleData secrets
    for (String secretName : gerrit.getModuleDataSecretNames()) {
      secretVersions.put(
          secretName,
          client
              .secrets()
              .inNamespace(gerrit.getMetadata().getNamespace())
              .withName(secretName)
              .get()
              .getMetadata()
              .getResourceVersion());
    }

    logger.atFine().log("Adding Secret versions: %s", secretVersions);
    status.setAppliedSecretVersions(secretVersions);
  }

  private static class SecretToGerritMapper implements SecondaryToPrimaryMapper<Secret> {
    private final EventSourceContext<Gerrit> context;

    public SecretToGerritMapper(EventSourceContext<Gerrit> context) {
      this.context = context;
    }

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(Secret secret) {
      return context
          .getPrimaryCache()
          .list(
              gerrit ->
                  isGerritSecretConfig(gerrit, secret) || isGerritModuleDataSecret(gerrit, secret))
          .map(ResourceID::fromResource)
          .collect(Collectors.toSet());
    }

    private boolean isGerritSecretConfig(Gerrit gerrit, Secret secret) {
      return gerrit.getSpec().getSecretRef().equals(secret.getMetadata().getName());
    }

    private boolean isGerritModuleDataSecret(Gerrit gerrit, Secret secret) {
      return gerrit.getSpec().getAllGerritModules().stream()
          .anyMatch(
              gerritModule ->
                  Objects.nonNull(gerritModule.getModuleData())
                      && Objects.nonNull(gerritModule.getModuleData().getSecretRef())
                      && gerritModule
                          .getModuleData()
                          .getSecretRef()
                          .equals(secret.getMetadata().getName()));
    }
  }
}
