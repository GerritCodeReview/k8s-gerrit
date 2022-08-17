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

package com.google.gerrit.k8s.operator.network;

import static com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource.HTTP_PORT_NAME;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource;
import io.fabric8.kubernetes.api.model.Service;
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

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-ingress")
public class GerritIngress extends CRUDKubernetesDependentResource<Ingress, GerritNetwork> {

  public GerritIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(gerritNetwork.getMetadata().getNamespace())
            .withName(gerritNetwork.getSpec().getCluster())
            .get();

    List<Service> gerritServices =
        client
            .resources(Service.class)
            .inNamespace(gerritNetwork.getMetadata().getNamespace())
            .withLabels(ServiceDependentResource.getLabels(gerritCluster))
            .list()
            .getItems();

    Ingress gerritIngress =
        new IngressBuilder()
            .withNewMetadata()
            .withName("gerrit-ingress")
            .withNamespace(gerritNetwork.getMetadata().getNamespace())
            .withLabels(gerritCluster.getLabels("gerrit-ingress", this.getClass().getSimpleName()))
            .withAnnotations(gerritNetwork.getSpec().getIngress().getAnnotations())
            .endMetadata()
            .withNewSpec()
            .withTls(getIngressTLS(gerritNetwork, gerritServices))
            .withRules(getIngressRules(gerritNetwork, gerritServices))
            .endSpec()
            .build();

    return gerritIngress;
  }

  private IngressTLS getIngressTLS(GerritNetwork gerritNetwork, List<Service> gerritServices) {
    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      List<String> hosts = new ArrayList<>();
      for (Service svc : gerritServices) {
        hosts.add(getFullHostname(svc, gerritNetwork));
      }
      return new IngressTLSBuilder()
          .withHosts(hosts)
          .withSecretName(gerritNetwork.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return new IngressTLS();
  }

  private List<IngressRule> getIngressRules(
      GerritNetwork gerritNetwork, List<Service> gerritServices) {
    List<IngressRule> ingressRules = new ArrayList<>();

    for (Service svc : gerritServices) {
      ingressRules.add(
          new IngressRuleBuilder()
              .withHost(getFullHostname(svc, gerritNetwork))
              .withNewHttp()
              .withPaths(getHTTPIngressPaths(svc))
              .endHttp()
              .build());
    }
    return ingressRules;
  }

  public HTTPIngressPath getHTTPIngressPaths(Service svc) {
    ServiceBackendPort port = new ServiceBackendPortBuilder().withName(HTTP_PORT_NAME).build();

    return new HTTPIngressPathBuilder()
        .withPathType("Prefix")
        .withPath("/")
        .withNewBackend()
        .withNewService()
        .withName(svc.getMetadata().getName())
        .withPort(port)
        .endService()
        .endBackend()
        .build();
  }

  public static String getFullHostname(Service svc, GerritNetwork gerritNetwork) {
    return String.format(
        "%s.%s", svc.getMetadata().getName(), gerritNetwork.getSpec().getIngress().getHost());
  }
}
