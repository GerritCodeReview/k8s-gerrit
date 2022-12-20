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
import static com.google.gerrit.k8s.operator.gerrit.GerritReconciler.PLUGIN_CONFIG_MAP_EVENT_SOURCE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;

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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
          name = "gerrit-plugin-configmap",
          type = GerritPluginConfigMapDependentResource.class,
          useEventSourceWithName = PLUGIN_CONFIG_MAP_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-statefulset",
          type = StatefulSetDependentResource.class,
          dependsOn = {
            "gerrit-configmap",
            "gerrit-init-configmap"
          }), // , "gerrit-plugin-configmap"?
      @Dependent(
          name = "gerrit-service",
          type = ServiceDependentResource.class,
          dependsOn = {"gerrit-statefulset"})
    })
public class GerritReconciler implements Reconciler<Gerrit>, EventSourceInitializer<Gerrit> {
  public static final String CONFIG_MAP_EVENT_SOURCE = "configmap-event-source";
  public static final String PLUGIN_CONFIG_MAP_EVENT_SOURCE = "plugin-configmap-event-source";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SECRET_EVENT_SOURCE_NAME = "secret-event-source";
  private final KubernetesClient client;


  public GerritReconciler(KubernetesClient client) {
    this.client = client;
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

    InformerEventSource<ConfigMap, Gerrit> pluginConfigmapEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, context).build(), context);

    Map<String, EventSource> eventSources =
        EventSourceInitializer.nameEventSources(gerritClusterEventSource);
    eventSources.put(CONFIG_MAP_EVENT_SOURCE, configmapEventSource);
    eventSources.put(SECRET_EVENT_SOURCE_NAME, secretEventSource);
    eventSources.put(PLUGIN_CONFIG_MAP_EVENT_SOURCE, pluginConfigmapEventSource);
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

  private void reloadGerritPlugin(String plugin) throws IOException {
	/**
	 * Wendy TODO: 
	 * Check for cases when plugin name isn't the same as the config.
	 * Look into solution for authentication for GerritRestApi
	 * (import com.urswolfer.gerrit.client.rest.GerritRestApi;)
	 * Will the operator need an internal user/permissions for using the REST api?
	 */
    String pluginName = plugin.replace(".config", "");
    String urlString = client.getMasterUrl().toString() + "plugins/" + pluginName + "~reload";
    logger.atInfo().log(urlString);
    URL url = new URL(urlString);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("POST");
    con.setDoOutput(true);
    con.getOutputStream();
    con.getInputStream().close();
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
          Map<String, String> resourceInfo = ((ConfigMap) r.getSingleResource().get()).getData();
          logger.atInfo().log("Configuration change in: %s", resourceInfo.keySet().toString());
          if (resourceInfo.containsKey("gerrit.config")) {
            // Restart when gerrit.config updated
            return true;
          } else {
            // Changes in gerrit plugins necessitating reload
            logger.atInfo().log("Plugins reloading.");
            for (String k : resourceInfo.keySet()) {
              try {
                reloadGerritPlugin(k);
              } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          }
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
            "Looking up Secret: %s; Installed secret resource version: %s; Resource version known"
                + " to Gerrit: %s",
            sec, secVersion, gerrit.getStatus().getAppliedSecretVersions().get(sec));
        return true;
      }
    }
    return false;
  }
}
