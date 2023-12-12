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

package com.google.gerrit.k8s.operator.v1beta2.api.model.network;

import com.google.gerrit.k8s.operator.v1beta2.api.model.shared.GerritClusterIngressConfig;
import java.util.ArrayList;
import java.util.List;

@Deprecated
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
}
