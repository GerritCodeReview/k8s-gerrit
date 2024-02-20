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

import com.google.gerrit.k8s.operator.api.model.shared.GerritClusterIngressConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GerritNetworkSpec {
  private GerritClusterIngressConfig ingress = new GerritClusterIngressConfig();
  private NetworkMember receiver;
  private NetworkMemberWithSsh primaryGerrit;
  private NetworkMemberWithSsh gerritReplica;

  public GerritClusterIngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(GerritClusterIngressConfig ingress) {
    this.ingress = ingress;
  }

  public NetworkMember getReceiver() {
    return receiver;
  }

  public void setReceiver(NetworkMember receiver) {
    this.receiver = receiver;
  }

  public NetworkMemberWithSsh getPrimaryGerrit() {
    return primaryGerrit;
  }

  public void setPrimaryGerrit(NetworkMemberWithSsh primaryGerrit) {
    this.primaryGerrit = primaryGerrit;
  }

  public NetworkMemberWithSsh getGerritReplica() {
    return gerritReplica;
  }

  public void setGerritReplica(NetworkMemberWithSsh gerritReplica) {
    this.gerritReplica = gerritReplica;
  }

  public List<NetworkMemberWithSsh> getGerrits() {
    List<NetworkMemberWithSsh> gerrits = new ArrayList<>();
    if (primaryGerrit != null) {
      gerrits.add(primaryGerrit);
    }
    if (gerritReplica != null) {
      gerrits.add(gerritReplica);
    }
    return gerrits;
  }

  @Override
  public int hashCode() {
    return Objects.hash(gerritReplica, ingress, primaryGerrit, receiver);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritNetworkSpec other = (GerritNetworkSpec) obj;
    return Objects.equals(gerritReplica, other.gerritReplica)
        && Objects.equals(ingress, other.ingress)
        && Objects.equals(primaryGerrit, other.primaryGerrit)
        && Objects.equals(receiver, other.receiver);
  }

  @Override
  public String toString() {
    return "GerritNetworkSpec [ingress="
        + ingress
        + ", receiver="
        + receiver
        + ", primaryGerrit="
        + primaryGerrit
        + ", gerritReplica="
        + gerritReplica
        + "]";
  }
}
