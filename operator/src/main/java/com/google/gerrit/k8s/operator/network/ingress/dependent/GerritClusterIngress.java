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

import static com.google.gerrit.k8s.operator.network.Constants.GERRIT_FORBIDDEN_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.PROJECTS_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.RECEIVE_PACK_URL_PATTERN;
import static com.google.gerrit.k8s.operator.network.Constants.UPLOAD_PACK_URL_PATTERN;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
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
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class GerritClusterIngress
    extends CRUDReconcileAddKubernetesDependentResource<Ingress, GerritNetwork> {
  public static final String INGRESS_NAME = "gerrit-ingress";
  public static final String SESSION_COOKIE_NAME = "Gerrit_Session";
  public static final Duration SESSION_COOKIE_TTL = Duration.ofSeconds(3600L);

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
                GerritClusterLabelFactory.create(
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

    StringBuilder configSnippet = new StringBuilder();
    if (gerritNetwork.hasPrimaryGerrit()) {
      configSnippet = createNginxConfigSnippetForbidHARoutes(configSnippet);
    }

    if (gerritNetwork.hasPrimaryGerrit() && gerritNetwork.hasGerritReplica()) {
      String svcName =
          new GerritService().getName(gerritNetwork.getSpec().getGerritReplica().getName());
      configSnippet =
          createNginxConfigSnippetGerritReplicaRouting(
              configSnippet,
              "service=git-upload-pack",
              gerritNetwork.getMetadata().getNamespace(),
              svcName);
    }
    if (gerritNetwork.hasReceiver() && gerritNetwork.hasGerritReplica()) {
      String svcName = ReceiverService.getName(gerritNetwork.getSpec().getReceiver().getName());
      configSnippet =
          createNginxConfigSnippetGerritReplicaRouting(
              configSnippet,
              "service=git-receive-pack",
              gerritNetwork.getMetadata().getNamespace(),
              svcName);
    }
    if (configSnippet.length() > 0) {
      annotations.put(
          "nginx.ingress.kubernetes.io/configuration-snippet", configSnippet.toString().trim());
    }

    annotations.put("nginx.ingress.kubernetes.io/affinity", "cookie");
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-name", SESSION_COOKIE_NAME);
    annotations.put("nginx.ingress.kubernetes.io/session-cookie-path", "/");
    annotations.put(
        "nginx.ingress.kubernetes.io/session-cookie-max-age",
        String.valueOf(SESSION_COOKIE_TTL.getSeconds()));
    annotations.put(
        "nginx.ingress.kubernetes.io/session-cookie-expires",
        String.valueOf(SESSION_COOKIE_TTL.getSeconds()));

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
  private StringBuilder createNginxConfigSnippetGerritReplicaRouting(
      StringBuilder configSnippet, String queryParam, String namespace, String svcName) {
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
    configSnippet.append("\";\n}\n");
    return configSnippet;
  }

  private StringBuilder createNginxConfigSnippetForbidHARoutes(StringBuilder configSnippet) {
    configSnippet.append("location ~ ");
    configSnippet.append(GERRIT_FORBIDDEN_URL_PATTERN);
    configSnippet.append(" {\n");
    configSnippet.append("  deny all;\n");
    configSnippet.append("  return 403;\n");
    configSnippet.append("}\n");
    return configSnippet;
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

    GerritService gerritService = new GerritService();
    String pathPrefix = gerritNetwork.getSpec().getIngress().getPathPrefix();
    List<HTTPIngressPath> paths = new ArrayList<>();
    // Order matters, since routing rules will be applied in order!
    if (!gerritNetwork.hasPrimaryGerrit() && gerritNetwork.hasGerritReplica()) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath(pathPrefix + "/")
              .withNewBackend()
              .withNewService()
              .withName(gerritService.getName(gerritNetwork.getSpec().getGerritReplica().getName()))
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
              .withPath(pathPrefix + UPLOAD_PACK_URL_PATTERN)
              .withNewBackend()
              .withNewService()
              .withName(gerritService.getName(gerritNetwork.getSpec().getGerritReplica().getName()))
              .withPort(port)
              .endService()
              .endBackend()
              .build());
    }
    if (gerritNetwork.hasPrimaryGerrit()) {
      paths.add(
          new HTTPIngressPathBuilder()
              .withPathType("Prefix")
              .withPath(pathPrefix + "/")
              .withNewBackend()
              .withNewService()
              .withName(gerritService.getName(gerritNetwork.getSpec().getPrimaryGerrit().getName()))
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

    HTTPIngressPathBuilder builder =
        new HTTPIngressPathBuilder()
            .withPathType("Prefix")
            .withNewBackend()
            .withNewService()
            .withName(svcName)
            .withPort(port)
            .endService()
            .endBackend();

    String pathPrefix = gerritNetwork.getSpec().getIngress().getPathPrefix();
    if (gerritNetwork.hasGerritReplica()) {
      for (String path : List.of(PROJECTS_URL_PATTERN, RECEIVE_PACK_URL_PATTERN)) {
        paths.add(builder.withPath(pathPrefix + path).build());
      }
    } else {
      paths.add(builder.withPath(pathPrefix + "/").build());
    }
    return paths;
  }
}
