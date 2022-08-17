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

import static com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource.HTTP_PORT_NAME;

import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource;
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
import java.util.stream.Collectors;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-ingress")
public class GerritIngress extends CRUDKubernetesDependentResource<Ingress, GerritCluster> {

  public GerritIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    List<Gerrit> gerrits =
        client
            .resources(Gerrit.class)
            .inNamespace(gerritCluster.getMetadata().getNamespace())
            .list()
            .getItems()
            .stream()
            .filter(gerrit -> GerritCluster.isGerritInstancePartOfCluster(gerrit, gerritCluster))
            .collect(Collectors.toList());

    Ingress gerritIngress =
        new IngressBuilder()
            .withNewMetadata()
            .withName("gerrit-ingress")
            .withNamespace(gerritCluster.getMetadata().getNamespace())
            .withLabels(gerritCluster.getLabels("gerrit-ingress", this.getClass().getSimpleName()))
            .withAnnotations(gerritCluster.getSpec().getIngress().getAnnotations())
            .endMetadata()
            .withNewSpec()
            .withTls(getIngressTLS(gerritCluster, gerrits))
            .withRules(getIngressRules(gerritCluster, gerrits))
            .endSpec()
            .build();

    return gerritIngress;
  }

  private IngressTLS getIngressTLS(GerritCluster gerritCluster, List<Gerrit> gerrits) {
    if (gerritCluster.getSpec().getIngress().getTls().isEnabled()) {
      List<String> hosts = new ArrayList<>();
      for (Gerrit gerrit : gerrits) {
        hosts.add(getFullHostname(ServiceDependentResource.getName(gerrit), gerritCluster));
      }
      return new IngressTLSBuilder()
          .withHosts(hosts)
          .withSecretName(gerritCluster.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return new IngressTLS();
  }

  private List<IngressRule> getIngressRules(GerritCluster gerritCluster, List<Gerrit> gerrits) {
    List<IngressRule> ingressRules = new ArrayList<>();

    for (Gerrit gerrit : gerrits) {
      String svcName = ServiceDependentResource.getName(gerrit);
      ingressRules.add(
          new IngressRuleBuilder()
              .withHost(getFullHostname(svcName, gerritCluster))
              .withNewHttp()
              .withPaths(getHTTPIngressPaths(svcName))
              .endHttp()
              .build());
    }
    return ingressRules;
  }

  public HTTPIngressPath getHTTPIngressPaths(String svcName) {
    ServiceBackendPort port = new ServiceBackendPortBuilder().withName(HTTP_PORT_NAME).build();

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

  public static String getFullHostname(String svcName, GerritCluster gerritCluster) {
    return String.format("%s.%s", svcName, gerritCluster.getSpec().getIngress().getHost());
  }
}
