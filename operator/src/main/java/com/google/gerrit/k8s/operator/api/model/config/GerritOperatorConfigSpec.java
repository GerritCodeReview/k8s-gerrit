// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.api.model.config;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.network.IngressType;
import java.util.Objects;

public class GerritOperatorConfigSpec {
  private ClusterMode clusterMode = ClusterMode.HIGH_AVAILABILITY;
  private IngressType ingressType = IngressType.NONE;
  private String clusterDomain = "cluster.local";

  public ClusterMode getClusterMode() {
    return clusterMode;
  }

  public void setClusterMode(ClusterMode clusterMode) {
    this.clusterMode = clusterMode;
  }

  public IngressType getIngressType() {
    return ingressType;
  }

  public void setIngressType(IngressType ingressType) {
    this.ingressType = ingressType;
  }

  public String getClusterDomain() {
    return clusterDomain;
  }

  public void setClusterDomain(String clusterDomain) {
    this.clusterDomain = clusterDomain;
  }

  @Override
  public int hashCode() {
    return Objects.hash(clusterDomain, clusterMode, ingressType);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritOperatorConfigSpec other = (GerritOperatorConfigSpec) obj;
    return Objects.equals(clusterDomain, other.clusterDomain)
        && clusterMode == other.clusterMode
        && ingressType == other.ingressType;
  }

  @Override
  public String toString() {
    return "GerritOperatorConfigSpec [clusterMode="
        + clusterMode
        + ", ingressType="
        + ingressType
        + ", clusterDomain="
        + clusterDomain
        + "]";
  }
}
