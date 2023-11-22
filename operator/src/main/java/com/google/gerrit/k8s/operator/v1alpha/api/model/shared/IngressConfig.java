// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.v1alpha.api.model.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Deprecated
public class IngressConfig {
  private boolean enabled;
  private String host;
  private boolean tlsEnabled;
  private GerritIngressSshConfig ssh = new GerritIngressSshConfig();

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

  public boolean isTlsEnabled() {
    return tlsEnabled;
  }

  public void setTlsEnabled(boolean tlsEnabled) {
    this.tlsEnabled = tlsEnabled;
  }

  public GerritIngressSshConfig getSsh() {
    return ssh;
  }

  public void setSsh(GerritIngressSshConfig ssh) {
    this.ssh = ssh;
  }

  @JsonIgnore
  public String getUrl() {
    String protocol = isTlsEnabled() ? "https" : "http";
    String hostname = getHost();
    return String.format("%s://%s", protocol, hostname);
  }

  @JsonIgnore
  public String getSshUrl() {
    String protocol = isTlsEnabled() ? "https" : "http";
    String hostname = getHost();
    return String.format("%s://%s", protocol, hostname);
  }
}
