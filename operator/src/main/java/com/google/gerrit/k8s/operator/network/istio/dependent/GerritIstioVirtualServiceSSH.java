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
import io.fabric8.istio.api.networking.v1beta1.L4MatchAttributesBuilder;
import io.fabric8.istio.api.networking.v1beta1.RouteDestination;
import io.fabric8.istio.api.networking.v1beta1.RouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1beta1.TCPRoute;
import io.fabric8.istio.api.networking.v1beta1.TCPRouteBuilder;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.api.networking.v1beta1.VirtualServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@KubernetesDependent(resourceDiscriminator = GerritIstioVirtualServiceSSHDiscriminator.class)
public class GerritIstioVirtualServiceSSH
    extends CRUDKubernetesDependentResource<VirtualService, GerritNetwork> {
  public static final String NAME_SUFFIX = "gerrit-ssh-virtual-service";

  public GerritIstioVirtualServiceSSH() {
    super(VirtualService.class);
  }

  @Override
  protected VirtualService desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
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
        .withHosts(collectHosts(gerritNetwork))
        .withGateways(GerritClusterIstioGateway.NAME)
        .withTcp(getTCPRoutes(gerritNetwork))
        .endSpec()
        .build();
  }

  private List<String> collectHosts(GerritNetwork gerritNetwork) {
    return gerritNetwork.getSpec().getGerrits().stream()
        .map(g -> g.getName() + "." + gerritNetwork.getSpec().getIngress().getHost())
        .collect(Collectors.toList());
  }

  private List<TCPRoute> getTCPRoutes(GerritNetwork gerritNetwork) {
    List<TCPRoute> routes = new ArrayList<>();
    for (NetworkMemberWithSsh gerrit : gerritNetwork.getSpec().getGerrits()) {
      if (gerritNetwork.getSpec().getIngress().getSsh().isEnabled() && gerrit.getSshPort() > 0) {
        routes.add(
            new TCPRouteBuilder()
                .withMatch(List.of(new L4MatchAttributesBuilder().withPort(29418).build()))
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
