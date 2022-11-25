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

package com.google.gerrit.k8s.operator.receiver;

import static com.google.gerrit.k8s.operator.receiver.ReceiverDeploymentDependentResource.HTTP_PORT;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class ReceiverServiceDependentResource
    extends CRUDKubernetesDependentResource<Service, Receiver> {
  public static final String HTTP_PORT_NAME = "http";

  public ReceiverServiceDependentResource() {
    super(Service.class);
  }

  @Override
  protected Service desired(Receiver receiver, Context<Receiver> context) {
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(receiver.getMetadata().getNamespace())
            .withName(receiver.getSpec().getCluster())
            .get();
    if (gerritCluster == null) {
      throw new IllegalStateException("The Gerrit cluster could not be found.");
    }

    return new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(receiver))
        .withNamespace(receiver.getMetadata().getNamespace())
        .withLabels(getLabels(gerritCluster))
        .endMetadata()
        .withNewSpec()
        .withType(receiver.getSpec().getService().getType())
        .withPorts(getServicePorts(receiver))
        .withSelector(ReceiverDeploymentDependentResource.getLabels(gerritCluster, receiver))
        .endSpec()
        .build();
  }

  public static String getName(Receiver receiver) {
    return receiver.getMetadata().getName();
  }

  public static Map<String, String> getLabels(GerritCluster gerritCluster) {
    return gerritCluster.getLabels("receiver-service", ReceiverReconciler.class.getSimpleName());
  }

  private static List<ServicePort> getServicePorts(Receiver receiver) {
    List<ServicePort> ports = new ArrayList<>();
    ports.add(
        new ServicePortBuilder()
            .withName(HTTP_PORT_NAME)
            .withPort(receiver.getSpec().getService().getHttpPort())
            .withNewTargetPort(HTTP_PORT)
            .build());
    return ports;
  }
}
