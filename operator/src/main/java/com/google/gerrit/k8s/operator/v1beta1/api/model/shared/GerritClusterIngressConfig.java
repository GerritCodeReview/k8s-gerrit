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

package com.google.gerrit.k8s.operator.v1beta1.api.model.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

@Deprecated
public class GerritClusterIngressConfig {
  private boolean enabled = false;
  private String host;
  private Map<String, String> annotations;
  private GerritIngressTlsConfig tls = new GerritIngressTlsConfig();
  private GerritIngressSshConfig ssh = new GerritIngressSshConfig();
  private GerritIngressAmbassadorConfig ambassador = new GerritIngressAmbassadorConfig();

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

  @JsonIgnore
  public String getFullHostnameForService(String svcName) {
    return getFullHostnameForService(svcName, getHost());
  }

  @JsonIgnore
  public static String getFullHostnameForService(String svcName, String ingressHost) {
    return String.format("%s.%s", svcName, ingressHost);
  }
}
