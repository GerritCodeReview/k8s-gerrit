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

package com.google.gerrit.k8s.operator.cluster;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource;
import io.fabric8.istio.api.networking.v1beta1.HTTPMatchRequestBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRoute;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GerritIstioVirtualService
    extends CRUDKubernetesDependentResource<VirtualService, GerritCluster> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GerritIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    String gerritClusterHost = gerritCluster.getSpec().getIngress().getHost();
    List<Gerrit> gerrits = getGerrits(gerritCluster);

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(getName(gerritCluster))
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(
            gerritCluster.getLabels(getName(gerritCluster), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(gerritClusterHost)
        .withGateways(GerritIstioGateway.NAME)
        .withHttp(getHTTPRoutes(gerrits))
        .endSpec()
        .build();
  }

  private List<Gerrit> getGerrits(GerritCluster gerritCluster) {
    return client
        .resources(Gerrit.class)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(g -> g.getSpec().getCluster().equals(gerritCluster.getMetadata().getName()))
        .collect(Collectors.toList());
  }

  public static String getName(GerritCluster gerritCluster) {
    return String.format("%s-http-virtual-service", gerritCluster.getMetadata().getName());
  }

  private List<HTTPRoute> getHTTPRoutes(List<Gerrit> gerrits) {
    List<HTTPRoute> routes = new ArrayList<>();
    for (Gerrit gerrit : gerrits) {
      switch (gerrit.getSpec().getMode()) {
        case REPLICA:
          routes.add(
              new HTTPRouteBuilder()
                  .withName("gerrit-replica-" + gerrit.getMetadata().getName())
                  .withMatch(
                      new HTTPMatchRequestBuilder()
                          .withNewUri()
                          .withNewStringMatchRegexType()
                          .withRegex("^.*info/refs?service=git-upload-pack$")
                          .endStringMatchRegexType()
                          .endUri()
                          .withIgnoreUriCase()
                          .withNewMethod()
                          .withNewStringMatchExactType()
                          .withExact("GET")
                          .endStringMatchExactType()
                          .endMethod()
                          .build(),
                      new HTTPMatchRequestBuilder()
                          .withNewUri()
                          .withNewStringMatchRegexType()
                          .withRegex("^.*/git-upload-pack$")
                          .endStringMatchRegexType()
                          .endUri()
                          .withIgnoreUriCase()
                          .withNewMethod()
                          .withNewStringMatchExactType()
                          .withExact("POST")
                          .endStringMatchExactType()
                          .endMethod()
                          .build())
                  .withRoute(getGerritHTTPDestinations(gerrit))
                  .build());
          break;
        case PRIMARY:
          routes.add(
              new HTTPRouteBuilder()
                  .withName("gerrit-primary-" + gerrit.getMetadata().getName())
                  .withRoute(getGerritHTTPDestinations(gerrit))
                  .build());
          break;
        default:
          logger.atFine().log(
              "Encountered unknown Gerrit mode when reconciling VirtualSErvice: %s",
              gerrit.getSpec().getMode());
      }
    }
    return routes;
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
