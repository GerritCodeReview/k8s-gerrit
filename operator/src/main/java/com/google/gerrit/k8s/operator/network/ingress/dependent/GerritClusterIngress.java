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

import static com.google.gerrit.k8s.operator.v1alpha.api.model.network.GerritNetwork.SESSION_COOKIE_NAME;

import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.v1alpha.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1alpha.api.model.network.GerritNetwork;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressSpecBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class GerritClusterIngress extends CRUDKubernetesDependentResource<Ingress, GerritNetwork> {
  private static final String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";
  private static final String RECEIVE_PACK_URL_PATTERN = "/.*/git-receive-pack";
  public static final String INGRESS_NAME = "gerrit-ingress";

  public GerritClusterIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    IngressSpecBuilder ingressSpecBuilder =
        new IngressSpecBuilder().withRules(getIngressRule(gerritNetwork));
    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      ingressSpecBuilder.withTls(getIngressTLS(gerritNetwork));
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
            .withAnnotations(getAnnotations(gerritNetwork))
            .endMetadata()
            .withSpec(ingressSpecBuilder.build())
            .build();

    return gerritIngress;
  }

  private Map<String, String> getAnnotations(GerritNetwork gerritNetwork) {
    Map<String, String> annotations = gerritNetwork.getSpec().getIngress().getAnnotations();
    if (annotations == null) {
      annotations = new HashMap<>();
    }
    annotations.put("nginx.ingress.kubernetes.io/use-regex", "true");
    annotations.put("kubernetes.io/ingress.class", "nginx");

    String configSnippet = "";
    if (gerritNetwork.hasPrimaryGerrit() && gerritNetwork.hasGerritReplica()) {
      String svcName = GerritService.getName(gerritNetwork.getSpec().getGerritReplica().getName());
      configSnippet =
          createNginxConfigSnippet(
              "service=git-upload-pack", gerritNetwork.getMetadata().getNamespace(), svcName);
    }
    if (gerritNetwork.hasReceiver()) {
      String svcName = ReceiverService.getName(gerritNetwork.getSpec().getReceiver().getName());
      configSnippet =
          createNginxConfigSnippet(
              "service=git-receive-pack", gerritNetwork.getMetadata().getNamespace(), svcName);
    }
    if (!configSnippet.isBlank()) {
      annotations.put("nginx.ingress.kubernetes.io/configuration-snippet", configSnippet);
    }

    annotations.put("nginx.ingress.kubernetes.io/affinity", "cookie");
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-name", SESSION_COOKIE_NAME);
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-path", "/");
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-max-age", "60");
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-expires", "60");

    return annotations;
  }

  /**
   * Creates a config snippet for the Nginx Ingress Controller [1]. This snippet will configure
   * Nginx to route the request based on the `service` query parameter.
   *
   * <p>If it is set to `git-upload-pack` it will route the request to the provided service.
   *
   * <p>[1]https://docs.nginx.com/nginx-ingress-controller/configuration/ingress-resources/advanced-configuration-with-snippets/
   *
   * @param namespace Namespace of the destination service.
   * @param svcName Name of the destination service.
   * @return configuration snippet
   */
  private String createNginxConfigSnippet(String queryParam, String namespace, String svcName) {
    StringBuilder configSnippet = new StringBuilder();
    configSnippet.append("if ($args ~ ");
    configSnippet.append(queryParam);
    configSnippet.append("){");
    configSnippet.append("\n");
    configSnippet.append("  set $proxy_upstream_name \"");
    configSnippet.append(namespace);
    configSnippet.append("-");
    configSnippet.append(svcName);
    configSnippet.append("-");
    configSnippet.append(GerritService.HTTP_PORT_NAME);
    configSnippet.append("\";\n");
    configSnippet.append("  set $proxy_host $proxy_upstream_name;");
    configSnippet.append("\n");
    configSnippet.append("  set $service_name \"");
    configSnippet.append(svcName);
    configSnippet.append("\";\n}");
    return configSnippet.toString();
  }

  private IngressTLS getIngressTLS(GerritNetwork gerritNetwork) {
    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      return new IngressTLSBuilder()
          .withHosts(gerritNetwork.getSpec().getIngress().getHost())
          .withSecretName(gerritNetwork.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return null;
  }

  private IngressRule getIngressRule(GerritNetwork gerritNetwork) {
    List<HTTPIngressPath> ingressPaths = new ArrayList<>();
    if (gerritNetwork.hasReceiver()) {
      ingressPaths.addAll(getReceiverIngressPaths(gerritNetwork));
    }
    if (gerritNetwork.hasGerrits()) {
      ingressPaths.addAll(getGerritHTTPIngressPaths(gerritNetwork));
    }

    if (ingressPaths.isEmpty()) {
      throw new IllegalStateException(
          "Failed to create Ingress: No Receiver or Gerrit in GerritCluster.");
    }

    return new IngressRuleBuilder()
        .withHost(gerritNetwork.getSpec().getIngress().getHost())
        .withNewHttp()
        .withPaths(ingressPaths)
        .endHttp()
        .build();
  }

  private List<HTTPIngressPath> getGerritHTTPIngressPaths(GerritNetwork gerritNetwork) {
    ServiceBackendPort port =
        new ServiceBackendPortBuilder().withName(GerritService.HTTP_PORT_NAME).build();

    List<HTTPIngressPath> paths = new ArrayList<>();
    // Order matters, since routing rules will be applied in order!
    if (!gerritNetwork.hasPrimaryGerrit() && gerritNetwork.hasGerritReplica()) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath("/")
              .withNewBackend()
              .withNewService()
              .withName(GerritService.getName(gerritNetwork.getSpec().getGerritReplica().getName()))
              .withPort(port)
              .endService()
              .endBackend()
              .build());
      return paths;
    }
    if (gerritNetwork.hasGerritReplica()) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath(UPLOAD_PACK_URL_PATTERN)
              .withNewBackend()
              .withNewService()
              .withName(GerritService.getName(gerritNetwork.getSpec().getGerritReplica().getName()))
              .withPort(port)
              .endService()
              .endBackend()
              .build());
    }
    if (gerritNetwork.hasPrimaryGerrit()) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath("/")
              .withNewBackend()
              .withNewService()
              .withName(GerritService.getName(gerritNetwork.getSpec().getPrimaryGerrit().getName()))
              .withPort(port)
              .endService()
              .endBackend()
              .build());
    }
    return paths;
  }

  private List<HTTPIngressPath> getReceiverIngressPaths(GerritNetwork gerritNetwork) {
    String svcName = ReceiverService.getName(gerritNetwork.getSpec().getReceiver().getName());
    List<HTTPIngressPath> paths = new ArrayList<>();
    ServiceBackendPort port =
        new ServiceBackendPortBuilder().withName(ReceiverService.HTTP_PORT_NAME).build();

    for (String path : List.of("/a/projects", RECEIVE_PACK_URL_PATTERN)) {
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
