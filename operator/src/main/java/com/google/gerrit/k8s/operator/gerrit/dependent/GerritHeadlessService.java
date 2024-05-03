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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritHeadlessServiceDiscriminator.class)
public class GerritHeadlessService
    extends CRUDReconcileAddKubernetesDependentResource<Service, Gerrit> {
  private static final String HTTP_PORT_NAME = "http";
  private static final String SSH_PORT_NAME = "ssh";
  private static final String HEADLESS_SUFFIX = "-headless";

  public GerritHeadlessService() {
    super(Service.class);
  }

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    return new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withType("ClusterIP")
        .withClusterIP("None")
        .withPorts(getServicePorts(gerrit))
        .withSelector(GerritStatefulSet.getSelectorLabels(gerrit))
        .endSpec()
        .build();
  }

  public static String getName(Gerrit gerrit) {
    return gerrit.getMetadata().getName() + HEADLESS_SUFFIX;
  }

  public static String getHostname(Gerrit gerrit) {
    return getHostname(getName(gerrit), gerrit.getMetadata().getNamespace());
  }

  public static String getHostname(String name, String namespace) {
    return String.format("%s.%s.svc.cluster.local", name, namespace);
  }

  public static String getUrl(Gerrit gerrit) {
    return String.format(
        "http://%s:%s", getHostname(gerrit), gerrit.getSpec().getService().getHttpPort());
  }

  public static Map<String, String> getLabels(Gerrit gerrit) {
    return GerritCluster.getLabels(
        gerrit.getMetadata().getName(), getName(gerrit), GerritReconciler.class.getSimpleName());
  }

  private static List<ServicePort> getServicePorts(Gerrit gerrit) {
    List<ServicePort> ports = new ArrayList<>();
    ports.add(
        new ServicePortBuilder()
            .withName(HTTP_PORT_NAME)
            .withPort(gerrit.getSpec().getService().getHttpPort())
            .withNewTargetPort(HTTP_PORT)
            .build());
    if (gerrit.isSshEnabled()) {
      ports.add(
          new ServicePortBuilder()
              .withName(SSH_PORT_NAME)
              .withPort(gerrit.getSpec().getService().getSshPort())
              .withNewTargetPort(SSH_PORT)
              .build());
    }
    return ports;
  }
}
