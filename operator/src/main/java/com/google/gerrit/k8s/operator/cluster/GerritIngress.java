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
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.List;

@KubernetesDependent(labelSelector = "app.kubernetes.io/component=gerrit-ingress")
public class GerritIngress extends CRUDKubernetesDependentResource<Ingress, GerritCluster> {

  public GerritIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    if (gerritCluster.getSpec().getIngress().isEnabled()) {
      Ingress gerritIngress =
          new IngressBuilder()
              .withNewMetadata()
              .withName("gerrit-ingress")
              .withNamespace(gerritCluster.getMetadata().getNamespace())
              .withLabels(
                  gerritCluster.getLabels("gerrit-ingress", this.getClass().getSimpleName()))
              .withAnnotations(gerritCluster.getSpec().getIngress().getAnnotations())
              .endMetadata()
              .withNewSpec()
              .withTls(getIngressTLS(gerritCluster))
              .withRules(getIngressRule(gerritCluster))
              .endSpec()
              .build();

      return gerritIngress;
    }
    return null;
  }

  private IngressTLS getIngressTLS(GerritCluster gerritCluster) {
    if (gerritCluster.getSpec().getIngress().getTls().isEnabled()) {
      return new IngressTLSBuilder()
          .withHosts(gerritCluster.getSpec().getIngress().getHost())
          .withSecretName(gerritCluster.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return null;
  }

  private IngressRule getIngressRule(GerritCluster gerritCluster) {
    return new IngressRuleBuilder()
        .withHost(gerritCluster.getSpec().getIngress().getHost())
        .withNewHttp()
        .withPaths(getHTTPIngressPaths(gerritCluster))
        .endHttp()
        .build();
  }

  public List<HTTPIngressPath> getHTTPIngressPaths(GerritCluster gerritCluster) {
    List<HTTPIngressPath> ingressPaths = new ArrayList<>();

    List<Service> gerritServices =
        client
            .resources(Service.class)
            .inNamespace(gerritCluster.getMetadata().getNamespace())
            .withLabels(ServiceDependentResource.getLabels(gerritCluster))
            .list()
            .getItems();

    for (Service svc : gerritServices) {
      ingressPaths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath("/")
              .withNewBackend()
              .withNewResource("v1", "Service", svc.getMetadata().getName())
              .endBackend()
              .build());
    }

    return ingressPaths;
  }
}
