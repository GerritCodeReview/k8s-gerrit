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

package com.google.gerrit.k8s.operator.network.istio.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.gerrit.k8s.operator.network.model.NetworkMemberWithSsh;
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
    extends CRUDKubernetesDependentResource<VirtualService, GerritNetwork> {
  private static final String UPLOAD_PACK_INFO_REF_URL_PATTERN = "^/(.*)/info/refs$";
  private static final String UPLOAD_PACK_URL_PATTERN = "^/(.*)/git-upload-pack$";
  public static final String NAME_SUFFIX = "gerrit-http-virtual-service";

  public GerritIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    String gerritClusterHost = gerritNetwork.getSpec().getIngress().getHost();

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(gerritNetwork.getDependentResourceName(NAME_SUFFIX))
        .withNamespace(gerritNetwork.getMetadata().getNamespace())
        .withLabels(
            GerritCluster.getLabels(
                gerritNetwork.getMetadata().getName(),
                gerritNetwork.getDependentResourceName(NAME_SUFFIX),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(gerritClusterHost)
        .withGateways(GerritClusterIstioGateway.NAME)
        .withHttp(getHTTPRoutes(gerritNetwork))
        .endSpec()
        .build();
  }

  private List<HTTPRoute> getHTTPRoutes(GerritNetwork gerritNetwork) {
    List<HTTPRoute> routes = new ArrayList<>();
    if (gerritNetwork.hasGerritReplica()) {
      routes.add(
          new HTTPRouteBuilder()
              .withName("gerrit-replica-" + gerritNetwork.getSpec().getGerritReplica())
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
              .withRoute(
                  getGerritHTTPDestinations(
                      gerritNetwork.getSpec().getGerritReplica(),
                      gerritNetwork.getMetadata().getNamespace()))
              .build());
    }
    if (gerritNetwork.hasGerritReplica()) {
      routes.add(
          new HTTPRouteBuilder()
              .withName("gerrit-primary-" + gerritNetwork.getSpec().getPrimaryGerrit())
              .withRoute(
                  getGerritHTTPDestinations(
                      gerritNetwork.getSpec().getPrimaryGerrit(),
                      gerritNetwork.getMetadata().getNamespace()))
              .build());
    }

    return routes;
  }

  private HTTPRouteDestination getGerritHTTPDestinations(
      NetworkMemberWithSsh networkMember, String namespace) {
    return new HTTPRouteDestinationBuilder()
        .withNewDestination()
        .withHost(GerritService.getHostname(networkMember.getName(), namespace))
        .withNewPort()
        .withNumber(networkMember.getHttpPort())
        .endPort()
        .endDestination()
        .build();
  }
}
