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

import static com.google.gerrit.k8s.operator.gerrit.StatefulSetDependentResource.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.StatefulSetDependentResource.SSH_PORT;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberDependentResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class ServiceDependentResource
    extends GerritClusterMemberDependentResource<Service, Gerrit> {
  public static final String HTTP_PORT_NAME = "http";

  public ServiceDependentResource() {
    super(Service.class);
  }

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster = getGerritCluster(gerrit);

    return new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerritCluster))
        .endMetadata()
        .withNewSpec()
        .withType(gerrit.getSpec().getService().getType())
        .withPorts(getServicePorts(gerrit))
        .withSelector(StatefulSetDependentResource.getSelectorLabels(gerritCluster, gerrit))
        .endSpec()
        .build();
  }

  public static String getName(Gerrit gerrit) {
    return gerrit.getMetadata().getName();
  }

  public static String getHostname(Gerrit gerrit) {
    return String.format(
        "%s.%s.svc.cluster.local", getName(gerrit), gerrit.getMetadata().getNamespace());
  }

  public static String getUrl(Gerrit gerrit) {
    return String.format(
        "http://%s:%s", getHostname(gerrit), gerrit.getSpec().getService().getHttpPort());
  }

  public static Map<String, String> getLabels(GerritCluster gerritCluster) {
    return gerritCluster.getLabels("gerrit-service", GerritReconciler.class.getSimpleName());
  }

  private static List<ServicePort> getServicePorts(Gerrit gerrit) {
    List<ServicePort> ports = new ArrayList<>();
    ports.add(
        new ServicePortBuilder()
            .withName(HTTP_PORT_NAME)
            .withPort(gerrit.getSpec().getService().getHttpPort())
            .withNewTargetPort(HTTP_PORT)
            .build());
    if (gerrit.getSpec().getService().isSshEnabled()) {
      ports.add(
          new ServicePortBuilder()
              .withName("ssh")
              .withPort(gerrit.getSpec().getService().getSshPort())
              .withNewTargetPort(SSH_PORT)
              .build());
    }
    return ports;
  }
}
