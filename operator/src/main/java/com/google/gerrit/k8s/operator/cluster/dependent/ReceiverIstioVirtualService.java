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
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.receiver.model.Receiver;
import io.fabric8.istio.api.networking.v1beta1.HTTPMatchRequest;
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
import java.util.ArrayList;
import java.util.List;

public class ReceiverIstioVirtualService
    extends CRUDKubernetesDependentResource<VirtualService, GerritCluster> {
  private static final String RECEIVER_INGRESS_SUBDOMAIN = "receiver";

  public ReceiverIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    Receiver receiver = gerritCluster.getSpec().getReceiver().toReceiver(gerritCluster);

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(getName(receiver))
        .withNamespace(receiver.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels(getName(receiver), this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(getHostname(gerritCluster))
        .withGateways(GerritIstioGateway.NAME)
        .withHttp(getHTTPRoute(receiver))
        .endSpec()
        .build();
  }

  public static String getHostname(GerritCluster gerritCluster) {
    String gerritClusterHost = gerritCluster.getSpec().getIngress().getHost();
    return RECEIVER_INGRESS_SUBDOMAIN + "." + gerritClusterHost;
  }

  public static String getName(Receiver receiver) {
    return receiver.getMetadata().getName();
  }

  public static String getName(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getReceiver().getMetadata().getName();
  }

  private HTTPRoute getHTTPRoute(Receiver receiver) {
    return new HTTPRouteBuilder()
        .withName("receiver")
        .withMatch(getReceiverMatches())
        .withRoute(getReceiverHTTPDestination(receiver))
        .build();
  }

  private HTTPRouteDestination getReceiverHTTPDestination(Receiver receiver) {
    return new HTTPRouteDestinationBuilder()
        .withNewDestination()
        .withHost(ReceiverService.getHostname(receiver))
        .withNewPort()
        .withNumber(receiver.getSpec().getService().getHttpPort())
        .endPort()
        .endDestination()
        .build();
  }

  private List<HTTPMatchRequest> getReceiverMatches() {
    List<HTTPMatchRequest> matches = new ArrayList<>();
    matches.add(
        new HTTPMatchRequestBuilder()
            .withUri(new StringMatchBuilder().withNewStringMatchPrefixType("/git/").build())
            .build());
    matches.add(
        new HTTPMatchRequestBuilder()
            .withUri(new StringMatchBuilder().withNewStringMatchPrefixType("/new/").build())
            .build());
    matches.add(
        new HTTPMatchRequestBuilder()
            .withUri(new StringMatchBuilder().withNewStringMatchPrefixType("/a/projects/").build())
            .build());
    return matches;
  }
}
