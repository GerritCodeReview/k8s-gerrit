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

import static com.google.gerrit.k8s.operator.network.Constants.GERRIT_FORBIDDEN_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.INFO_REFS_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.PROJECTS_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.RECEIVE_PACK_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.UPLOAD_PACK_URL_PATTERN;

import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import com.google.gerrit.k8s.operator.v1beta3.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta3.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.v1beta3.api.model.network.NetworkMember;
import com.google.gerrit.k8s.operator.v1beta3.api.model.network.NetworkMemberWithSsh;
import io.fabric8.istio.api.networking.v1beta1.HTTPMatchRequest;
import io.fabric8.istio.api.networking.v1beta1.HTTPMatchRequestBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRoute;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1beta1.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.L4MatchAttributesBuilder;
import io.fabric8.istio.api.networking.v1beta1.RouteDestination;
import io.fabric8.istio.api.networking.v1beta1.RouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.StringMatchBuilder;
import io.fabric8.istio.api.networking.v1beta1.TCPRoute;
import io.fabric8.istio.api.networking.v1beta1.TCPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class GerritIstioVirtualService
    extends CRUDReconcileAddKubernetesDependentResource<VirtualService, GerritNetwork> {
  public static final String NAME_SUFFIX = "gerrit-http-virtual-service";

  public GerritIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    String gerritClusterHost = gerritNetwork.getSpec().getIngress().getHost();
    String namespace = gerritNetwork.getMetadata().getNamespace();

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(gerritNetwork.getDependentResourceName(NAME_SUFFIX))
        .withNamespace(namespace)
        .withLabels(
            GerritCluster.getLabels(
                gerritNetwork.getMetadata().getName(),
                gerritNetwork.getDependentResourceName(NAME_SUFFIX),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(gerritClusterHost)
        .withGateways(namespace + "/" + GerritClusterIstioGateway.NAME)
        .withHttp(getHTTPRoutes(gerritNetwork))
        .withTcp(getTCPRoutes(gerritNetwork))
        .endSpec()
        .build();
  }

  private List<HTTPRoute> getHTTPRoutes(GerritNetwork gerritNetwork) {
    String namespace = gerritNetwork.getMetadata().getNamespace();
    List<HTTPRoute> routes = new ArrayList<>();
    if (gerritNetwork.hasReceiver()) {
      routes.add(
          new HTTPRouteBuilder()
              .withName("receiver-" + gerritNetwork.getSpec().getReceiver().getName())
              .withMatch(getReceiverMatches())
              .withRoute(
                  getReceiverHTTPDestination(gerritNetwork.getSpec().getReceiver(), namespace))
              .build());
    }
    if (gerritNetwork.hasGerritReplica()) {
      HTTPRouteBuilder routeBuilder =
          new HTTPRouteBuilder()
              .withName("gerrit-replica-" + gerritNetwork.getSpec().getGerritReplica().getName());
      if (gerritNetwork.hasPrimaryGerrit()) {
        routeBuilder = routeBuilder.withMatch(getGerritReplicaMatches());
      }
      routes.add(
          routeBuilder
              .withRoute(
                  getGerritHTTPDestinations(gerritNetwork.getSpec().getGerritReplica(), namespace))
              .build());
    }
    if (gerritNetwork.hasPrimaryGerrit()) {
      HTTPRouteDestination dest =
          getGerritHTTPDestinations(gerritNetwork.getSpec().getPrimaryGerrit(), namespace);
      routes.add(
          new HTTPRouteBuilder()
              .withName("forbidden-routes-" + gerritNetwork.getSpec().getPrimaryGerrit().getName())
              .withRoute(dest)
              .withMatch(getGerritForbiddenMatches())
              .withNewFault()
              .withNewAbort()
              .withNewHTTPFaultInjectionAbortHttpStatusErrorType()
              .withHttpStatus(403)
              .endHTTPFaultInjectionAbortHttpStatusErrorType()
              .withNewPercentage()
              .withValue(100D)
              .endPercentage()
              .endAbort()
              .endFault()
              .build());
      routes.add(
          new HTTPRouteBuilder()
              .withName("gerrit-primary-" + gerritNetwork.getSpec().getPrimaryGerrit().getName())
              .withRoute(dest)
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

  private List<HTTPMatchRequest> getGerritForbiddenMatches() {
    List<HTTPMatchRequest> matches = new ArrayList<>();
    matches.add(
        new HTTPMatchRequestBuilder()
            .withNewUri()
            .withNewStringMatchRegexType()
            .withRegex(GERRIT_FORBIDDEN_URL_PATTERN)
            .endStringMatchRegexType()
            .endUri()
            .build());
    return matches;
  }

  private List<HTTPMatchRequest> getGerritReplicaMatches() {
    List<HTTPMatchRequest> matches = new ArrayList<>();
    matches.add(
        new HTTPMatchRequestBuilder()
            .withNewUri()
            .withNewStringMatchRegexType()
            .withRegex(INFO_REFS_PATTERN)
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
            .build());
    matches.add(
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
            .build());
    return matches;
  }

  private HTTPRouteDestination getReceiverHTTPDestination(
      NetworkMember receiver, String namespace) {
    return new HTTPRouteDestinationBuilder()
        .withNewDestination()
        .withHost(ReceiverService.getHostname(receiver.getName(), namespace))
        .withNewPort()
        .withNumber(receiver.getHttpPort())
        .endPort()
        .endDestination()
        .build();
  }

  private List<HTTPMatchRequest> getReceiverMatches() {
    List<HTTPMatchRequest> matches = new ArrayList<>();
    matches.add(
        new HTTPMatchRequestBuilder()
            .withUri(
                new StringMatchBuilder().withNewStringMatchRegexType(PROJECTS_URL_PATTERN).build())
            .build());
    matches.add(
        new HTTPMatchRequestBuilder()
            .withNewUri()
            .withNewStringMatchRegexType()
            .withRegex(RECEIVE_PACK_URL_PATTERN)
            .endStringMatchRegexType()
            .endUri()
            .build());
    matches.add(
        new HTTPMatchRequestBuilder()
            .withNewUri()
            .withNewStringMatchRegexType()
            .withRegex(INFO_REFS_PATTERN)
            .endStringMatchRegexType()
            .endUri()
            .withQueryParams(
                Map.of(
                    "service",
                    new StringMatchBuilder()
                        .withNewStringMatchExactType("git-receive-pack")
                        .build()))
            .build());
    return matches;
  }

  private List<TCPRoute> getTCPRoutes(GerritNetwork gerritNetwork) {
    List<TCPRoute> routes = new ArrayList<>();
    for (NetworkMemberWithSsh gerrit : gerritNetwork.getSpec().getGerrits()) {
      if (gerritNetwork.getSpec().getIngress().getSsh().isEnabled() && gerrit.getSshPort() > 0) {
        routes.add(
            new TCPRouteBuilder()
                .withMatch(
                    List.of(new L4MatchAttributesBuilder().withPort(gerrit.getSshPort()).build()))
                .withRoute(
                    getGerritTCPDestination(gerrit, gerritNetwork.getMetadata().getNamespace()))
                .build());
      }
    }
    return routes;
  }

  private RouteDestination getGerritTCPDestination(
      NetworkMemberWithSsh networkMember, String namespace) {
    return new RouteDestinationBuilder()
        .withNewDestination()
        .withHost(GerritService.getHostname(networkMember.getName(), namespace))
        .withNewPort()
        .withNumber(networkMember.getSshPort())
        .endPort()
        .endDestination()
        .build();
  }
}
