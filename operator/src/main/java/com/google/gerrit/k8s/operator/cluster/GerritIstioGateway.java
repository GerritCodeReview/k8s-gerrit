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

import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.GatewayBuilder;
import io.fabric8.istio.api.networking.v1beta1.Server;
import io.fabric8.istio.api.networking.v1beta1.ServerBuilder;
import io.fabric8.istio.api.networking.v1beta1.ServerTLSSettingsTLSmode;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GerritIstioGateway extends CRUDKubernetesDependentResource<Gateway, GerritCluster> {
  public static final String NAME = "gerrit-istio-gateway";

  public GerritIstioGateway() {
    super(Gateway.class);
  }

  @Override
  protected Gateway desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    return new GatewayBuilder()
        .withNewMetadata()
        .withName(NAME)
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withLabels(gerritCluster.getLabels(NAME, this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withSelector(Map.of("istio", "ingressgateway"))
        .withServers(configureServers(gerritCluster))
        .endSpec()
        .build();
  }

  private List<Server> configureServers(GerritCluster gerritCluster) {
    List<Server> servers = new ArrayList<>();

    servers.add(
        new ServerBuilder()
            .withNewPort()
            .withName("http")
            .withNumber(80)
            .withProtocol("HTTP")
            .endPort()
            .withHosts(gerritCluster.getSpec().getIngress().computeHostnames(client, gerritCluster))
            .withNewTls()
            .withHttpsRedirect(gerritCluster.getSpec().getIngress().getTls().isEnabled())
            .endTls()
            .build());

    if (gerritCluster.getSpec().getIngress().getTls().isEnabled()) {
      servers.add(
          new ServerBuilder()
              .withNewPort()
              .withName("https")
              .withNumber(443)
              .withProtocol("HTTPS")
              .endPort()
              .withHosts(
                  gerritCluster.getSpec().getIngress().computeHostnames(client, gerritCluster))
              .withNewTls()
              .withMode(ServerTLSSettingsTLSmode.SIMPLE)
              .withCredentialName(gerritCluster.getSpec().getIngress().getTls().getSecret())
              .endTls()
              .build());
    }

    boolean sshEnabled =
        client.resources(Gerrit.class).list().getItems().stream()
            .anyMatch(g -> g.getSpec().getService().isSshEnabled());

    if (sshEnabled) {
      servers.add(
          new ServerBuilder()
              .withNewPort()
              .withName("ssh")
              .withNumber(29418)
              .withProtocol("TCP")
              .endPort()
              .withHosts(
                  gerritCluster.getSpec().getIngress().computeHostnames(client, gerritCluster))
              .build());
    }

    return servers;
  }
}
