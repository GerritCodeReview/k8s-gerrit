// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.config.dependent;

import static com.google.gerrit.k8s.operator.server.HttpServer.PORT;

import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent()
public class GerritOperatorService
    extends CRUDReconcileAddKubernetesDependentResource<Service, GerritOperatorConfig> {
  public static final String SERVICE_NAME = "gerrit-operator";
  public static final int SERVICE_PORT = 8080;

  public GerritOperatorService() {
    super(Service.class);
  }

  @Override
  protected Service desired(
      GerritOperatorConfig gerritOperatorConfig, Context<GerritOperatorConfig> context) {
    ServicePort port =
        new ServicePortBuilder()
            .withName("http")
            .withPort(SERVICE_PORT)
            .withNewTargetPort(PORT)
            .withProtocol("TCP")
            .build();

    return new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(SERVICE_NAME)
        .withNamespace(gerritOperatorConfig.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
        .withType("ClusterIP")
        .withPorts(port)
        .withSelector(Map.of("app", "gerrit-operator"))
        .endSpec()
        .build();
  }
}
