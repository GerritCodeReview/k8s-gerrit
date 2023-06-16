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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import io.fabric8.istio.api.networking.v1beta1.HTTPMatchRequestBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRoute;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.StringMatchBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritIstioVirtualServiceDiscriminator.class)
public class GerritIstioVirtualService
    extends CRUDKubernetesDependentResource<VirtualService, GerritCluster> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String UPLOAD_PACK_INFO_REF_URL_PATTERN = "^/(.*)/info/refs$";
  private static final String UPLOAD_PACK_URL_PATTERN = "^/(.*)/git-upload-pack$";
  public static final String NAME_SUFFIX = "gerrit-http-virtual-service";

  public GerritIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    String gerritClusterHost = gerritCluster.getSpec().getIngress().getHost();

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(gerritCluster.getDependentResourceName(NAME_SUFFIX))
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(
            gerritCluster.getLabels(
                gerritCluster.getDependentResourceName(NAME_SUFFIX),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(gerritClusterHost)
        .withGateways(GerritClusterIstioGateway.NAME)
        .withHttp(getHTTPRoutes(gerritCluster))
        .endSpec()
        .build();
  }

  private List<HTTPRoute> getHTTPRoutes(GerritCluster gerritCluster) {
    List<GerritTemplate> gerrits = gerritCluster.getSpec().getGerrits();
    ArrayListMultimap<GerritMode, HTTPRoute> routesByMode = ArrayListMultimap.create();
    for (GerritTemplate gerrit : gerrits) {
      switch (gerrit.getSpec().getMode()) {
        case REPLICA:
          routesByMode.put(
              GerritMode.REPLICA,
              new HTTPRouteBuilder()
                  .withName("gerrit-replica-" + gerrit.getMetadata().getName())
                  .withMatch(
                      new HTTPMatchRequestBuilder()
                          .withNewUri()
                          .withNewStringMatchRegexType()
                          .withRegex(UPLOAD_PACK_INFO_REF_URL_PATTERN)
                          .endStringMatchRegexType()
                          .endUri()
                          .withQueryParams(
                              Map.of(
                                  "service",
                                  new StringMatchBuilder()
                                      .withNewStringMatchExactType("git-upload-pack")
                                      .build()))
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
                          .withRegex(UPLOAD_PACK_URL_PATTERN)
                          .endStringMatchRegexType()
                          .endUri()
                          .withIgnoreUriCase()
                          .withNewMethod()
                          .withNewStringMatchExactType()
                          .withExact("POST")
                          .endStringMatchExactType()
                          .endMethod()
                          .build())
                  .withRoute(getGerritHTTPDestinations(gerrit, gerritCluster))
                  .build());
          break;
        case PRIMARY:
          routesByMode.put(
              GerritMode.PRIMARY,
              new HTTPRouteBuilder()
                  .withName("gerrit-primary-" + gerrit.getMetadata().getName())
                  .withRoute(getGerritHTTPDestinations(gerrit, gerritCluster))
                  .build());
          break;
        default:
          logger.atFine().log(
              "Encountered unknown Gerrit mode when reconciling VirtualSErvice: %s",
              gerrit.getSpec().getMode());
      }
    }

    List<HTTPRoute> routes = new ArrayList<>();
    routes.addAll(routesByMode.get(GerritMode.REPLICA));
    routes.addAll(routesByMode.get(GerritMode.PRIMARY));
    return routes;
  }

  private HTTPRouteDestination getGerritHTTPDestinations(
      GerritTemplate gerrit, GerritCluster gerritCluster) {
    return new HTTPRouteDestinationBuilder()
        .withNewDestination()
        .withHost(GerritService.getHostname(gerrit.toGerrit(gerritCluster)))
        .withNewPort()
        .withNumber(gerrit.getSpec().getService().getHttpPort())
        .endPort()
        .endDestination()
        .build();
  }
}
