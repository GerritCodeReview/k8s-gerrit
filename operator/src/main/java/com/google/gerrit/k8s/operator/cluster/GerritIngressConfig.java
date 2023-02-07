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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.receiver.Receiver;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GerritIngressConfig {
  private boolean enabled = false;
  private IngressType type = IngressType.NONE;
  private String host;
  private Map<String, String> annotations;
  private GerritIngressTlsConfig tls = new GerritIngressTlsConfig();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public IngressType getType() {
    return type;
  }

  public void setType(IngressType type) {
    this.type = type;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  public GerritIngressTlsConfig getTls() {
    return tls;
  }

  public void setTls(GerritIngressTlsConfig tls) {
    this.tls = tls;
  }

  public enum IngressType {
    NONE,
    INGRESS,
    ISTIO
  }

  @JsonIgnore
  public String getFullHostnameForService(String svcName) {
    return String.format("%s.%s", svcName, getHost());
  }

  @JsonIgnore
  public String getUrl(String svcName) {
    String protocol = getTls().isEnabled() ? "https" : "http";
    return String.format("%s://%s", protocol, getFullHostnameForService(svcName));
  }

  @JsonIgnore
  public List<String> computeHostnames(KubernetesClient client, GerritCluster gerritCluster) {
    List<String> hostnames = new ArrayList<>();
    hostnames.addAll(computeGerritHostnames(client, gerritCluster));
    hostnames.addAll(computeReceiverHostnames(client, gerritCluster));
    return hostnames;
  }

  @JsonIgnore
  public List<String> computeGerritHostnames(KubernetesClient client, GerritCluster gerritCluster) {
    return client
        .resources(Gerrit.class)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(gerrit -> GerritCluster.isMemberPartOfCluster(gerrit.getSpec(), gerritCluster))
        .map(gerrit -> getFullHostnameForService(gerrit.getMetadata().getName()))
        .collect(Collectors.toList());
  }

  @JsonIgnore
  public List<String> computeReceiverHostnames(
      KubernetesClient client, GerritCluster gerritCluster) {
    return client
        .resources(Receiver.class)
        .inNamespace(gerritCluster.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(r -> GerritCluster.isMemberPartOfCluster(r.getSpec(), gerritCluster))
        .map(r -> getFullHostnameForService(r.getMetadata().getName()))
        .collect(Collectors.toList());
  }
}
