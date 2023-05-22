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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import io.fabric8.istio.api.networking.v1beta1.L4MatchAttributesBuilder;
import io.fabric8.istio.api.networking.v1beta1.RouteDestination;
import io.fabric8.istio.api.networking.v1beta1.RouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.TCPRoute;
import io.fabric8.istio.api.networking.v1beta1.TCPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GerritIstioVirtualServiceSSH
    extends CRUDKubernetesDependentResource<VirtualService, GerritCluster> {

  public GerritIstioVirtualServiceSSH() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    String gerritClusterHost = gerritCluster.getSpec().getIngress().getHost();
    List<GerritTemplate> gerrits = gerritCluster.getSpec().getGerrits();

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(getName(gerritCluster))
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(
            gerritCluster.getLabels(getName(gerritCluster), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(collectHosts(gerrits, gerritClusterHost))
        .withGateways(GerritIstioGateway.NAME)
        .withTcp(getTCPRoutes(gerrits, gerritCluster))
        .endSpec()
        .build();
  }

  public static String getName(GerritCluster gerritCluster) {
    return String.format("%s-ssh-virtual-service", gerritCluster.getMetadata().getName());
  }

  private List<String> collectHosts(List<GerritTemplate> gerrits, String gerritClusterHost) {
    return gerrits.stream()
        .map(g -> g.getMetadata().getName() + "." + gerritClusterHost)
        .collect(Collectors.toList());
  }

  private List<TCPRoute> getTCPRoutes(List<GerritTemplate> gerrits, GerritCluster gerritCluster) {
    List<TCPRoute> routes = new ArrayList<>();
    for (GerritTemplate gerrit : gerrits) {
      if (gerrit.getSpec().getService().isSshEnabled()) {
        routes.add(
            new TCPRouteBuilder()
                .withMatch(List.of(new L4MatchAttributesBuilder().withPort(29418).build()))
                .withRoute(getGerritTCPDestination(gerrit, gerritCluster))
                .build());
      }
    }
    return routes;
  }

  private RouteDestination getGerritTCPDestination(
      GerritTemplate gerrit, GerritCluster gerritCluster) {
    return new RouteDestinationBuilder()
        .withNewDestination()
        .withHost(GerritService.getHostname(gerrit.toGerrit(gerritCluster)))
        .withNewPort()
        .withNumber(gerrit.getSpec().getService().getSshPort())
        .endPort()
        .endDestination()
        .build();
  }
}
