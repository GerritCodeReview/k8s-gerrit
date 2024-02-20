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

package com.google.gerrit.k8s.operator.api.model.network;

import com.google.gerrit.k8s.operator.api.model.shared.HttpSshServiceConfig;
import java.util.Objects;

public class NetworkMemberWithSsh extends NetworkMember {
  private int sshPort = 29418;

  public NetworkMemberWithSsh() {}

  public NetworkMemberWithSsh(String name, HttpSshServiceConfig serviceConfig) {
    super(name, serviceConfig);
    this.sshPort = serviceConfig.getSshPort();
  }

  public int getSshPort() {
    return sshPort;
  }

  public void setSshPort(int sshPort) {
    this.sshPort = sshPort;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(sshPort);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    NetworkMemberWithSsh other = (NetworkMemberWithSsh) obj;
    return sshPort == other.sshPort;
  }

  @Override
  public String toString() {
    return "NetworkMemberWithSsh [sshPort=" + sshPort + "]";
  }
}
