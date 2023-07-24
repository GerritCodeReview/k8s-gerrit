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

package com.google.gerrit.k8s.operator.network.model;

import com.google.gerrit.k8s.operator.shared.model.GerritClusterIngressConfig;
import java.util.ArrayList;
import java.util.List;

public class GerritNetworkSpec {
  private GerritClusterIngressConfig ingress = new GerritClusterIngressConfig();
  private String receiver = "";
  private List<String> gerrits = new ArrayList<>();

  public GerritClusterIngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(GerritClusterIngressConfig ingress) {
    this.ingress = ingress;
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    this.receiver = receiver;
  }

  public List<String> getGerrits() {
    return gerrits;
  }

  public void setGerrits(List<String> gerrits) {
    this.gerrits = gerrits;
  }
}
