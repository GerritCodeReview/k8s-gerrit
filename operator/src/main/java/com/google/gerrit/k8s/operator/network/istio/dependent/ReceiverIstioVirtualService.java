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
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
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
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;

@KubernetesDependent(resourceDiscriminator = ReceiverIstioVirtualServiceDiscriminator.class)
public class ReceiverIstioVirtualService
    extends CRUDKubernetesDependentResource<VirtualService, GerritNetwork> {
  private static final String RECEIVER_INGRESS_SUBDOMAIN = "receiver";
  public static final String NAME_SUFFIX = "receiver-virtual-service";

  public ReceiverIstioVirtualService() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Receiver receiver =
        client
            .resources(Receiver.class)
            .inNamespace(gerritNetwork.getMetadata().getNamespace())
            .withName(gerritNetwork.getSpec().getReceiver())
            .get();

    return new VirtualServiceBuilder()
        .withNewMetadata()
        .withName(gerritNetwork.getDependentResourceName(NAME_SUFFIX))
        .withNamespace(receiver.getMetadata().getNamespace())
        .withLabels(
            GerritCluster.getLabels(
                gerritNetwork.getMetadata().getName(),
                gerritNetwork.getDependentResourceName(NAME_SUFFIX),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHosts(getHostname(gerritNetwork))
        .withGateways(GerritClusterIstioGateway.NAME)
        .withHttp(getHTTPRoute(receiver))
        .endSpec()
        .build();
  }

  public static String getHostname(GerritNetwork gerritNetwork) {
    String gerritClusterHost = gerritNetwork.getSpec().getIngress().getHost();
    return RECEIVER_INGRESS_SUBDOMAIN + "." + gerritClusterHost;
  }

  public static String getName(GerritNetwork gerritNetwork) {
    return gerritNetwork.getSpec().getReceiver();
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
