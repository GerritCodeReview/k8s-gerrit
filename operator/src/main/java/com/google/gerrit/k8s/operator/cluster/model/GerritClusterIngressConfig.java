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

package com.google.gerrit.k8s.operator.cluster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

public class GerritClusterIngressConfig {
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
    return getFullHostnameForService(svcName, getHost());
  }

  @JsonIgnore
  public static String getFullHostnameForService(String svcName, String ingressHost) {
    return String.format("%s.%s", svcName, ingressHost);
  }

  @JsonIgnore
  public String getUrl(String svcName) {
    return getUrl(svcName, getTls().isEnabled(), getHost(), getType());
  }

  @JsonIgnore
  public static String getUrl(
      String svcName, boolean tlsEnabled, String ingressHost, IngressType ingressType) {
    String protocol = tlsEnabled ? "https" : "http";
    String hostname =
        ingressType == IngressType.ISTIO
            ? ingressHost
            : getFullHostnameForService(svcName, ingressHost);
    return String.format("%s://%s", protocol, hostname);
  }
}
