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

package com.google.gerrit.k8s.operator.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.cluster.model.GerritClusterIngressConfig.IngressType;

public class IngressConfig {
  private IngressType type = IngressType.NONE;
  private String host;
  private boolean tls;

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

  public boolean isTls() {
    return tls;
  }

  public void setTls(boolean tls) {
    this.tls = tls;
  }

  @JsonIgnore
  public String getFullHostnameForService(String svcName) {
    return String.format("%s.%s", svcName, getHost());
  }

  @JsonIgnore
  public String getUrl(String svcName) {
    String protocol = isTls() ? "https" : "http";
    String hostname =
        getType() == IngressType.ISTIO ? getHost() : getFullHostnameForService(svcName);
    return String.format("%s://%s", protocol, hostname);
  }
}
