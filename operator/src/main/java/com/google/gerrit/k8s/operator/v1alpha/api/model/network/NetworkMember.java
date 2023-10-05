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

package com.google.gerrit.k8s.operator.v1alpha.api.model.network;

import com.google.gerrit.k8s.operator.v1alpha.api.model.shared.HttpServiceConfig;

@Deprecated
public class NetworkMember {
  private String name;
  private int httpPort = 8080;

  public NetworkMember() {}

  public NetworkMember(String name, HttpServiceConfig serviceConfig) {
    this.name = name;
    this.httpPort = serviceConfig.getHttpPort();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }
}
