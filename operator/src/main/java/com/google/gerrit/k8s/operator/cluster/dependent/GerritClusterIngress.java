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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@KubernetesDependent
public class GerritClusterIngress extends CRUDKubernetesDependentResource<Ingress, GerritCluster> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";
  public static final String INGRESS_NAME = "gerrit-ingress";

  public GerritClusterIngress() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(GerritCluster gerritCluster, Context<GerritCluster> context) {
    Ingress gerritIngress =
        new IngressBuilder()
            .withNewMetadata()
            .withName("gerrit-ingress")
            .withNamespace(gerritCluster.getMetadata().getNamespace())
            .withLabels(gerritCluster.getLabels("gerrit-ingress", this.getClass().getSimpleName()))
            .withAnnotations(getAnnotations(gerritCluster))
            .endMetadata()
            .withNewSpec()
            .withTls(getIngressTLS(gerritCluster))
            .withRules(getIngressRule(gerritCluster))
            .endSpec()
            .build();

    return gerritIngress;
  }

  private Map<String, String> getAnnotations(GerritCluster gerritCluster) {
    Map<String, String> annotations = gerritCluster.getSpec().getIngress().getAnnotations();
    annotations.put("nginx.ingress.kubernetes.io/use-regex", "true");
    annotations.put("kubernetes.io/ingress.class", "nginx");

    Optional<GerritTemplate> gerritReplica =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.REPLICA))
            .findFirst();
    if (gerritReplica.isPresent()) {
      String svcName = GerritService.getName(gerritReplica.get());
      StringBuilder configSnippet = new StringBuilder();
      configSnippet.append("if ($args ~ service=git-upload-pack){");
      configSnippet.append("\n");
      configSnippet.append("  set $proxy_upstream_name \"");
      configSnippet.append(gerritCluster.getMetadata().getNamespace());
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
      annotations.put(
          "nginx.ingress.kubernetes.io/configuration-snippet", configSnippet.toString());
    }
    return annotations;
  }

  private IngressTLS getIngressTLS(GerritCluster gerritCluster) {
    if (gerritCluster.getSpec().getIngress().getTls().isEnabled()) {
      return new IngressTLSBuilder()
          .withHosts(gerritCluster.getSpec().getIngress().getHost())
          .withSecretName(gerritCluster.getSpec().getIngress().getTls().getSecret())
          .build();
    }
    return new IngressTLS();
  }

  private IngressRule getIngressRule(GerritCluster gerritCluster) {
    List<HTTPIngressPath> ingressPaths = new ArrayList<>();
    if (!gerritCluster.getSpec().getGerrits().isEmpty()) {
      ingressPaths.addAll(getGerritHTTPIngressPaths(gerritCluster));
    }
    if (gerritCluster.getSpec().getReceiver() != null) {
      ingressPaths.addAll(getReceiverIngressPaths(gerritCluster));
    }

    if (ingressPaths.isEmpty()) {
      throw new IllegalStateException(
          "Failed to create Ingress: No Receiver or Gerrit in GerritCluster.");
    }

    return new IngressRuleBuilder()
        .withHost(gerritCluster.getSpec().getIngress().getHost())
        .withNewHttp()
        .withPaths(ingressPaths)
        .endHttp()
        .build();
  }

  private List<HTTPIngressPath> getGerritHTTPIngressPaths(GerritCluster gerritCluster) {
    ServiceBackendPort port =
        new ServiceBackendPortBuilder().withName(GerritService.HTTP_PORT_NAME).build();

    ArrayListMultimap<GerritMode, HTTPIngressPath> pathsByMode = ArrayListMultimap.create();
    List<Gerrit> gerrits =
        gerritCluster.getSpec().getGerrits().stream()
            .map(g -> g.toGerrit(gerritCluster))
            .collect(Collectors.toList());
    for (Gerrit gerrit : gerrits) {
      switch (gerrit.getSpec().getMode()) {
        case REPLICA:
          pathsByMode.put(
              GerritMode.REPLICA,
              new HTTPIngressPathBuilder()
                  .withPathType("Prefix")
                  .withPath(UPLOAD_PACK_URL_PATTERN)
                  .withNewBackend()
                  .withNewService()
                  .withName(GerritService.getName(gerrit))
                  .withPort(port)
                  .endService()
                  .endBackend()
                  .build());
          break;
        case PRIMARY:
          pathsByMode.put(
              GerritMode.PRIMARY,
              new HTTPIngressPathBuilder()
                  .withPathType("Prefix")
                  .withPath("/")
                  .withNewBackend()
                  .withNewService()
                  .withName(GerritService.getName(gerrit))
                  .withPort(port)
                  .endService()
                  .endBackend()
                  .build());
          break;
        default:
          logger.atFine().log(
              "Encountered unknown Gerrit mode when reconciling Ingress: %s",
              gerrit.getSpec().getMode());
      }
    }

    List<HTTPIngressPath> paths = new ArrayList<>();
    paths.addAll(pathsByMode.get(GerritMode.REPLICA));
    paths.addAll(pathsByMode.get(GerritMode.PRIMARY));
    return paths;
  }

  private List<HTTPIngressPath> getReceiverIngressPaths(GerritCluster gerritCluster) {
    String svcName = ReceiverService.getName(gerritCluster.getSpec().getReceiver());
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
