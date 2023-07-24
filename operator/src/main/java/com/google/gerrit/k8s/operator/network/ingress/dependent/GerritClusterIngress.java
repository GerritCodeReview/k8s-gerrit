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

package com.google.gerrit.k8s.operator.network.ingress.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@KubernetesDependent
public class GerritClusterIngress extends CRUDKubernetesDependentResource<Ingress, GerritNetwork> {
  public static final String INGRESS_NAME = "gerrit-ingress";

  public GerritClusterIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    List<String> hosts = new ArrayList<>();
    List<IngressRule> ingressRules = new ArrayList<>();

    if (!gerritNetwork.getSpec().getReceiver().isBlank()) {
      ingressRules.add(getReceiverIngressRule(gerritNetwork));
      hosts.add(
          gerritNetwork
              .getSpec()
              .getIngress()
              .getFullHostnameForService(gerritNetwork.getSpec().getReceiver()));
    }

    ingressRules.addAll(getGerritIngressRules(gerritNetwork));
    for (String gerrit : gerritNetwork.getSpec().getGerrits()) {
      hosts.add(gerritNetwork.getSpec().getIngress().getFullHostnameForService(gerrit));
    }

    Ingress gerritIngress =
        new IngressBuilder()
            .withNewMetadata()
            .withName("gerrit-ingress")
            .withNamespace(gerritNetwork.getMetadata().getNamespace())
            .withLabels(
                GerritCluster.getLabels(
                    gerritNetwork.getMetadata().getName(),
                    "gerrit-ingress",
                    this.getClass().getSimpleName()))
            .withAnnotations(gerritNetwork.getSpec().getIngress().getAnnotations())
            .endMetadata()
            .withNewSpec()
            .withTls(getIngressTLS(gerritNetwork, hosts))
            .withRules(ingressRules)
            .endSpec()
            .build();

    return gerritIngress;
  }

  private IngressTLS getIngressTLS(GerritNetwork gerritNetwork, List<String> hosts) {
    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      return new IngressTLSBuilder()
          .withHosts(hosts)
          .withSecretName(gerritNetwork.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return new IngressTLS();
  }

  private List<IngressRule> getGerritIngressRules(GerritNetwork gerritNetwork) {
    List<IngressRule> ingressRules = new ArrayList<>();

    for (String gerritName : gerritNetwork.getSpec().getGerrits()) {
      String gerritSvcName = GerritService.getName(gerritName);
      ingressRules.add(
          new IngressRuleBuilder()
              .withHost(
                  gerritNetwork.getSpec().getIngress().getFullHostnameForService(gerritSvcName))
              .withNewHttp()
              .withPaths(getGerritHTTPIngressPath(gerritSvcName))
              .endHttp()
              .build());
    }

    return ingressRules;
  }

  private IngressRule getReceiverIngressRule(GerritNetwork gerritNetwork) {
    String receiverName = gerritNetwork.getSpec().getReceiver();
    return new IngressRuleBuilder()
        .withHost(gerritNetwork.getSpec().getIngress().getFullHostnameForService(receiverName))
        .withNewHttp()
        .withPaths(getReceiverIngressPaths(ReceiverService.getName(receiverName)))
        .endHttp()
        .build();
  }

  public HTTPIngressPath getGerritHTTPIngressPath(String svcName) {
    ServiceBackendPort port =
        new ServiceBackendPortBuilder().withName(GerritService.HTTP_PORT_NAME).build();

    return new HTTPIngressPathBuilder()
        .withPathType("Prefix")
        .withPath("/")
        .withNewBackend()
        .withNewService()
        .withName(svcName)
        .withPort(port)
        .endService()
        .endBackend()
        .build();
  }

  public List<HTTPIngressPath> getReceiverIngressPaths(String svcName) {
    List<HTTPIngressPath> paths = new ArrayList<>();
    ServiceBackendPort port =
        new ServiceBackendPortBuilder().withName(ReceiverService.HTTP_PORT_NAME).build();

    for (String path : Set.of("/a/projects", "/new", "/git")) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath(path)
              .withNewBackend()
              .withNewService()
              .withName(svcName)
              .withPort(port)
              .endService()
              .endBackend()
              .build());
    }
    return paths;
  }
}
