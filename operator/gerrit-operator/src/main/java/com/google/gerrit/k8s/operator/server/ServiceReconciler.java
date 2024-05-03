// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.server;

import static com.google.gerrit.k8s.operator.server.HttpServer.PORT;

import java.util.Map;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.LifecycleManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;

@Singleton
public class ServiceReconciler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String SERVICE_NAME = "gerrit-operator";
  public static final int SERVICE_PORT = 8080;

  private final KubernetesClient client;
  private final LifecycleManager lifecycleManager;

  private final String namespace;

  private Operator operator;
  private Service svc;

  @Inject
  public ServiceReconciler(
      LifecycleManager lifecycleManager,
      KubernetesClient client,
      @Named("Namespace") String namespace) {
    this.lifecycleManager = lifecycleManager;
    this.client = client;
    this.namespace = namespace;
  }

  public void start() throws Exception {
    lifecycleManager.addShutdownHook(
        new Runnable() {
          @Override
          public void run() {
            shutdown();
          }
        });
    applyService();
  }

  public void shutdown() {
    client.resource(svc).delete();
    operator.stop();
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

    logger.atInfo().log(String.format("Applying Service for Gerrit Operator: %s", svc.toString()));
    client.resource(svc).createOrReplace();
  }

}
