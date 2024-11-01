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

package com.google.gerrit.k8s.operator.network.istio.dependent;

import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.JGROUPS_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritHeadlessService;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.istio.api.networking.v1beta1.PortBuilder;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryBuilder;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryLocation;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntryResolution;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GerritIstioServiceEntries
    extends CRUDReconcileAddKubernetesDependentResource<ServiceEntry, GerritNetwork>
    implements Deleter<GerritNetwork>, BulkDependentResource<ServiceEntry, GerritNetwork> {

  public GerritIstioServiceEntries() {
    super(ServiceEntry.class);
  }

  private ServiceEntry desired(
      String name, String podName, GerritNetwork gerritNetwork, String host) {
    String namespace = gerritNetwork.getMetadata().getNamespace();
    return new ServiceEntryBuilder()
        .withNewMetadata()
        .withName(name)
        .withNamespace(namespace)
        .withLabels(
            GerritClusterLabelFactory.create(
                gerritNetwork.getMetadata().getName(), podName, this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withLocation(ServiceEntryLocation.MESH_INTERNAL)
        .withResolution(ServiceEntryResolution.DNS)
        .withHosts(host)
        .withPorts(
            List.of(
                new PortBuilder()
                    .withName("ssh-primary")
                    .withNumber(SSH_PORT)
                    .withProtocol("TCP")
                    .build(),
                new PortBuilder()
                    .withName("http")
                    .withNumber(HTTP_PORT)
                    .withProtocol("HTTP")
                    .build(),
                new PortBuilder()
                    .withName("jgroups")
                    .withNumber(JGROUPS_PORT)
                    .withProtocol("TCP")
                    .build()))
        .endSpec()
        .build();
  }

  private String getServiceHost(String podName, String serviceName, String namespace) {
    String clusterDomain = System.getenv().getOrDefault("CLUSTER_DOMAIN", "cluster.local");
    return String.format("%s.%s.%s.svc.%s", podName, serviceName, namespace, clusterDomain);
  }

  @Override
  public Map<String, ServiceEntry> desiredResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Map<String, ServiceEntry> serviceEntries = new HashMap<>();
    if (gerritNetwork.hasPrimaryGerrit()) {
      String primaryGerritName = gerritNetwork.getSpec().getPrimaryGerrit().getName();

      StatefulSet sts =
          context
              .getClient()
              .resources(StatefulSet.class)
              .inNamespace(gerritNetwork.getMetadata().getNamespace())
              .withName(GerritStatefulSet.getName(primaryGerritName))
              .get();

      if (sts == null) {
        return serviceEntries;
      }

      for (int i = 0; i < sts.getSpec().getReplicas(); i++) {
        String podName = String.format("%s-%d", primaryGerritName, i);
        String namespace = gerritNetwork.getMetadata().getNamespace();
        serviceEntries.put(
            podName,
            desired(
                podName,
                podName,
                gerritNetwork,
                getServiceHost(
                    podName,
                    new GerritService()
                        .getName(gerritNetwork.getSpec().getPrimaryGerrit().getName()),
                    namespace)));
        serviceEntries.put(
            podName + "-headless",
            desired(
                podName + "-headless",
                podName,
                gerritNetwork,
                getServiceHost(
                    podName,
                    new GerritHeadlessService()
                        .getName(gerritNetwork.getSpec().getPrimaryGerrit().getName()),
                    namespace)));
      }
    }
    return serviceEntries;
  }

  @Override
  public Map<String, ServiceEntry> getSecondaryResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Set<ServiceEntry> serviceEntries = context.getSecondaryResources(ServiceEntry.class);
    Map<String, ServiceEntry> result = new HashMap<>(serviceEntries.size());
    for (ServiceEntry se : serviceEntries) {
      result.put(se.getMetadata().getName(), se);
    }
    return result;
  }
}
