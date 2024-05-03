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

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.GatewayBuilder;
import io.fabric8.istio.api.networking.v1beta1.Server;
import io.fabric8.istio.api.networking.v1beta1.ServerBuilder;
import io.fabric8.istio.api.networking.v1beta1.ServerTLSSettingsTLSmode;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;

public class GerritClusterIstioGateway
    extends CRUDReconcileAddKubernetesDependentResource<Gateway, GerritNetwork> {
  public static final String NAME = "gerrit-istio-gateway";

  public GerritClusterIstioGateway() {
    super(Gateway.class);
  }

  @Override
  public Gateway desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    return new GatewayBuilder()
        .withNewMetadata()
        .withName(NAME)
        .withNamespace(gerritNetwork.getMetadata().getNamespace())
        .withLabels(
            GerritClusterLabelFactory.create(
                gerritNetwork.getMetadata().getName(), NAME, this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withSelector(gerritNetwork.getSpec().getIngress().getIstio().getGatewaySelector())
        .withServers(configureServers(gerritNetwork))
        .endSpec()
        .build();
  }

  private List<Server> configureServers(GerritNetwork gerritNetwork) {
    List<Server> servers = new ArrayList<>();
    String gerritClusterHost = gerritNetwork.getSpec().getIngress().getHost();

    servers.add(
        new ServerBuilder()
            .withNewPort()
            .withName("http")
            .withNumber(80)
            .withProtocol("HTTP")
            .endPort()
            .withHosts(gerritClusterHost)
            .withNewTls()
            .withHttpsRedirect(gerritNetwork.getSpec().getIngress().getTls().isEnabled())
            .endTls()
            .build());

    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      servers.add(
          new ServerBuilder()
              .withNewPort()
              .withName("https")
              .withNumber(443)
              .withProtocol("HTTPS")
              .endPort()
              .withHosts(gerritClusterHost)
              .withNewTls()
              .withMode(ServerTLSSettingsTLSmode.SIMPLE)
              .withCredentialName(gerritNetwork.getSpec().getIngress().getTls().getSecret())
              .endTls()
              .build());
    }

    if (gerritNetwork.getSpec().getIngress().getSsh().isEnabled() && gerritNetwork.hasGerrits()) {
      if (gerritNetwork.hasPrimaryGerrit()) {
        servers.add(
            new ServerBuilder()
                .withNewPort()
                .withName("ssh-primary")
                .withNumber(gerritNetwork.getSpec().getPrimaryGerrit().getSshPort())
                .withProtocol("TCP")
                .endPort()
                .withHosts(gerritClusterHost)
                .build());
      }
      if (gerritNetwork.hasGerritReplica()) {
        servers.add(
            new ServerBuilder()
                .withNewPort()
                .withName("ssh-replica")
                .withNumber(gerritNetwork.getSpec().getGerritReplica().getSshPort())
                .withProtocol("TCP")
                .endPort()
                .withHosts(gerritClusterHost)
                .build());
      }
    }

    return servers;
  }
}
