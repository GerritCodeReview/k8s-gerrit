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
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.JGROUPS_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class GerritAbstractService
    extends CRUDReconcileAddKubernetesDependentResource<Service, Gerrit> {

  public static final String HTTP_PORT_NAME = "http";
  public static final String SSH_PORT_NAME = "ssh";
  public static final String JGROUPS_PORT_NAME = "jgroups";

  abstract ServiceSpec getSpec(Gerrit gerrit);

  public abstract String getName(String gerritName);

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    return new ServiceBuilder().withMetadata(getMetaData(gerrit)).withSpec(getSpec(gerrit)).build();
  }

  private Map<String, String> getLabels(Gerrit gerrit) {
    return GerritClusterLabelFactory.create(
        gerrit.getMetadata().getName(),
        getComponentName(gerrit),
        GerritReconciler.class.getSimpleName());
  }

  private ObjectMeta getMetaData(Gerrit gerrit) {
    return new ObjectMetaBuilder()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .build();
  }

  String getComponentName(Gerrit gerrit) {
    return getName(gerrit);
  }

  public GerritAbstractService() {
    super(Service.class);
  }

  public String getName(Gerrit gerrit) {
    return getName(gerrit.getMetadata().getName());
  }

  public String getHostname(Gerrit gerrit) {
    return getHostname(getName(gerrit), gerrit.getMetadata().getNamespace());
  }

  public String getHostname(String name, String namespace) {
    return String.format("%s.%s.svc.cluster.local", getName(name), namespace);
  }

  public String getUrl(Gerrit gerrit) {
    return String.format(
        "http://%s:%s", getHostname(gerrit), gerrit.getSpec().getService().getHttpPort());
  }

  public static List<ServicePort> getServicePorts(Gerrit gerrit) {
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
    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      ports.add(
          new ServicePortBuilder()
              .withName(JGROUPS_PORT_NAME)
              .withPort(JGROUPS_PORT)
              .withNewTargetPort(JGROUPS_PORT)
              .build());
    }
    return ports;
  }
}
