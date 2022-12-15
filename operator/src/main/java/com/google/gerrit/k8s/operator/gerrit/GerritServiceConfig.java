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

package com.google.gerrit.k8s.operator.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

public class GerritServiceConfig implements Serializable {
  private static final long serialVersionUID = 1L;

  String type = "NodePort";
  int httpPort = 80;
  Integer sshPort;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public Integer getSshPort() {
    return sshPort;
  }

  public void setSshPort(int sshPort) {
    this.sshPort = sshPort;
  }

  @JsonIgnore
  public boolean isSshEnabled() {
    return this.sshPort != null;
  }
}
