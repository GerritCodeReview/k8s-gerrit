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

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberDependentResource;
import com.google.gerrit.k8s.operator.cluster.GerritIstioGateway;
import io.fabric8.istio.api.networking.v1beta1.HTTPRoute;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.L4MatchAttributesBuilder;
import io.fabric8.istio.api.networking.v1beta1.RouteDestination;
import io.fabric8.istio.api.networking.v1beta1.RouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.TCPRoute;
import io.fabric8.istio.api.networking.v1beta1.TCPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;

public class GerritIstioVirtualService
    extends GerritClusterMemberDependentResource<VirtualService, Gerrit> {

  public GerritIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster = getGerritCluster(gerrit);

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels(getName(gerrit), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(
            gerritCluster.getSpec().getIngress().computeGerritHostnames(client, gerritCluster))
        .withGateways(GerritIstioGateway.NAME)
        .withHttp(getHTTPRoute(gerrit))
        .withTcp(getTCPRoutes(gerrit))
        .endSpec()
        .build();
  }

  public static String getName(Gerrit gerrit) {
    return gerrit.getMetadata().getName();
  }

  private List<TCPRoute> getTCPRoutes(Gerrit gerrit) {
    List<TCPRoute> routes = new ArrayList<>();
    if (gerrit.getSpec().getService().isSshEnabled()) {
      routes.add(
          new TCPRouteBuilder()
              .withMatch(List.of(new L4MatchAttributesBuilder().withPort(29418).build()))
              .withRoute(getGerritTCPDestination(gerrit))
              .build());
    }
    return routes;
  }

  private RouteDestination getGerritTCPDestination(Gerrit gerrit) {
    return new RouteDestinationBuilder()
        .withNewDestination()
        .withHost(ServiceDependentResource.getHostname(gerrit))
        .withNewPort()
        .withNumber(gerrit.getSpec().getService().getSshPort())
        .endPort()
        .endDestination()
        .build();
  }

  private HTTPRoute getHTTPRoute(Gerrit gerrit) {
    return new HTTPRouteBuilder()
        .withName("gerrit")
        .withRoute(getGerritHTTPDestinations(gerrit))
        .build();
  }

  private HTTPRouteDestination getGerritHTTPDestinations(Gerrit gerrit) {
    return new HTTPRouteDestinationBuilder()
        .withNewDestination()
        .withHost(ServiceDependentResource.getHostname(gerrit))
        .withNewPort()
        .withNumber(gerrit.getSpec().getService().getHttpPort())
        .endPort()
        .endDestination()
        .build();
  }
}
