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
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.receiver.model.Receiver;
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
import java.util.stream.Collectors;

@KubernetesDependent
public class GerritIngress extends CRUDKubernetesDependentResource<Ingress, GerritCluster> {
  public static final String INGRESS_NAME = "gerrit-ingress";

  public GerritIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    List<Gerrit> gerrits =
        gerritCluster.getSpec().getGerrits().stream()
            .map(g -> g.toGerrit(gerritCluster))
            .collect(Collectors.toList());

    List<String> hosts = new ArrayList<>();
    List<IngressRule> ingressRules = new ArrayList<>();

    if (gerritCluster.getSpec().getReceiver() != null) {
      Receiver receiver = gerritCluster.getSpec().getReceiver().toReceiver(gerritCluster);
      ingressRules.add(getReceiverIngressRule(gerritCluster, receiver));
      hosts.add(
          gerritCluster
              .getSpec()
              .getIngress()
              .getFullHostnameForService(receiver.getMetadata().getName()));
    }

    ingressRules.addAll(getGerritIngressRules(gerritCluster, gerrits));
    for (Gerrit gerrit : gerrits) {
      hosts.add(
          gerritCluster
              .getSpec()
              .getIngress()
              .getFullHostnameForService(gerrit.getMetadata().getName()));
    }

    Ingress gerritIngress =
        new IngressBuilder()
            .withNewMetadata()
            .withName("gerrit-ingress")
            .withNamespace(gerritCluster.getMetadata().getNamespace())
            .withLabels(gerritCluster.getLabels("gerrit-ingress", this.getClass().getSimpleName()))
            .withAnnotations(gerritCluster.getSpec().getIngress().getAnnotations())
            .endMetadata()
            .withNewSpec()
            .withTls(getIngressTLS(gerritCluster, hosts))
            .withRules(ingressRules)
            .endSpec()
            .build();

    return gerritIngress;
  }

  private IngressTLS getIngressTLS(GerritCluster gerritCluster, List<String> hosts) {
    if (gerritCluster.getSpec().getIngress().getTls().isEnabled()) {
      return new IngressTLSBuilder()
          .withHosts(hosts)
          .withSecretName(gerritCluster.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return new IngressTLS();
  }

  private List<IngressRule> getGerritIngressRules(
      GerritCluster gerritCluster, List<Gerrit> gerrits) {
    List<IngressRule> ingressRules = new ArrayList<>();

    for (Gerrit gerrit : gerrits) {
      String gerritSvcName = GerritService.getName(gerrit);
      ingressRules.add(
          new IngressRuleBuilder()
              .withHost(
                  gerritCluster.getSpec().getIngress().getFullHostnameForService(gerritSvcName))
              .withNewHttp()
              .withPaths(getGerritHTTPIngressPath(gerritSvcName))
              .endHttp()
              .build());
    }

    return ingressRules;
  }

  private IngressRule getReceiverIngressRule(GerritCluster gerritCluster, Receiver receiver) {
    return new IngressRuleBuilder()
        .withHost(
            gerritCluster
                .getSpec()
                .getIngress()
                .getFullHostnameForService(receiver.getMetadata().getName()))
        .withNewHttp()
        .withPaths(getReceiverIngressPaths(ReceiverService.getName(receiver)))
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
