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

package com.google.gerrit.k8s.operator;

import static com.google.gerrit.k8s.operator.server.HttpServer.PORT;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfigSpec;
import com.google.gerrit.k8s.operator.config.GerritOperatorConfigReconciler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Singleton
public class GerritOperator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String SERVICE_NAME = "gerrit-operator";
  public static final int SERVICE_PORT = 8080;

  private final KubernetesClient client;
  private final LifecycleManager lifecycleManager;

  private final ReconcilerSetProvider reconcilerProvider;

  private final String namespace;

  private Operator operator;
  private boolean started;
  private Service svc;

  @Inject
  public GerritOperator(
      LifecycleManager lifecycleManager,
      KubernetesClient client,
      ReconcilerSetProvider reconcilerProvider,
      @Named("Namespace") String namespace) {
    this.lifecycleManager = lifecycleManager;
    this.client = client;
    this.reconcilerProvider = reconcilerProvider;
    this.namespace = namespace;
  }

  public void init() throws Exception {
    GerritOperatorConfig operatorConfig = getInitialGerritOperatorConfig();
    // The GerritOperatorConfigReconciler will apply the configuration, register the Reconcilers and
    // start the operator thread.
    new GerritOperatorConfigReconciler(this).reconcile(operatorConfig, null);
    lifecycleManager.addShutdownHook(
        new Runnable() {
          @Override
          public void run() {
            shutdown();
          }
        });
    applyService();
  }

  public void start() {
    if (!started) {
      logger.atInfo().log("Starting operator.");
      operator =
          new Operator(
              overrider ->
                  overrider
                      .withSSABasedCreateUpdateMatchForDependentResources(false)
                      .withCloseClientOnStop(false)
                      .withKubernetesClient(client));
      registerReconcilers();
      operator.start();
      started = true;
      logger.atInfo().log("Operator has been started.");
    }
  }

  public void stop() {
    if (started) {
      logger.atInfo().log("Stopping operator.");
      operator.stop();
      started = false;
    }
  }

  public void restart() {
    stop();
    start();
  }

  public void shutdown() {
    client.resource(svc).delete();
    operator.stop(Duration.ofSeconds(10));
    client.close();
  }

  private GerritOperatorConfig getInitialGerritOperatorConfig() {
    List<GerritOperatorConfig> operatorConfigs =
        client.resources(GerritOperatorConfig.class).inNamespace(namespace).list().getItems();
    if (operatorConfigs.isEmpty()) {
      GerritOperatorConfig cfg = new GerritOperatorConfig();
      cfg.setSpec(new GerritOperatorConfigSpec());
      return cfg;
    } else if (operatorConfigs.size() > 1) {
      logger.atWarning().log(
          "More than one GerritOpertaorConfig found. Using %s.",
          operatorConfigs.get(0).getMetadata().getName());
    }
    return operatorConfigs.get(0);
  }

  private void registerReconcilers() {
    operator.register(new GerritOperatorConfigReconciler(this));
    for (Reconciler<?> reconciler : reconcilerProvider.get()) {
      logger.atInfo().log("Registering reconciler: %s", reconciler.getClass().getSimpleName());
      operator.register(reconciler);
    }
  }

  private void applyService() {
    ServicePort port =
        new ServicePortBuilder()
            .withName("http")
            .withPort(SERVICE_PORT)
            .withNewTargetPort(PORT)
            .withProtocol("TCP")
            .build();
    svc =
        new ServiceBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName(SERVICE_NAME)
            .withNamespace(namespace)
            .endMetadata()
            .withNewSpec()
            .withType("ClusterIP")
            .withPorts(port)
            .withSelector(Map.of("app", "gerrit-operator"))
            .endSpec()
            .build();

    logger.atInfo().log("Applying Service for Gerrit Operator: %s", svc.toString());
    client.resource(svc).createOrReplace();
  }
}
