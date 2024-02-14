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

package com.google.gerrit.k8s.operator.api.model.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.Objects;

public class GerritClusterIngressConfig {
  private boolean enabled = false;
  private String host;
  private Map<String, String> annotations;
  private GerritIngressTlsConfig tls = new GerritIngressTlsConfig();
  private GerritIngressSshConfig ssh = new GerritIngressSshConfig();
  private GerritIngressAmbassadorConfig ambassador = new GerritIngressAmbassadorConfig();
  private GerritIstioConfig istio = new GerritIstioConfig();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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

  public GerritIngressSshConfig getSsh() {
    return ssh;
  }

  public void setSsh(GerritIngressSshConfig ssh) {
    this.ssh = ssh;
  }

  public GerritIngressAmbassadorConfig getAmbassador() {
    return ambassador;
  }

  public void setAmbassador(GerritIngressAmbassadorConfig ambassador) {
    this.ambassador = ambassador;
  }

  public GerritIstioConfig getIstio() {
    return istio;
  }

  public void setIstio(GerritIstioConfig istio) {
    this.istio = istio;
  }

  @JsonIgnore
  public String getFullHostnameForService(String svcName) {
    return getFullHostnameForService(svcName, getHost());
  }

  @JsonIgnore
  public static String getFullHostnameForService(String svcName, String ingressHost) {
    return String.format("%s.%s", svcName, ingressHost);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ambassador, annotations, enabled, host, istio, ssh, tls);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritClusterIngressConfig other = (GerritClusterIngressConfig) obj;
    return Objects.equals(ambassador, other.ambassador)
        && Objects.equals(annotations, other.annotations)
        && enabled == other.enabled
        && Objects.equals(host, other.host)
        && Objects.equals(istio, other.istio)
        && Objects.equals(ssh, other.ssh)
        && Objects.equals(tls, other.tls);
  }

  @Override
  public String toString() {
    return "GerritClusterIngressConfig [enabled="
        + enabled
        + ", host="
        + host
        + ", annotations="
        + annotations
        + ", tls="
        + tls
        + ", ssh="
        + ssh
        + ", ambassador="
        + ambassador
        + ", istio="
        + istio
        + "]";
  }
}
