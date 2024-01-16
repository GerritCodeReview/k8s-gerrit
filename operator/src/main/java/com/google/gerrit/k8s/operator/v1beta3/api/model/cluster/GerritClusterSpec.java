<<<<<<< PATCH SET (267093 [Operator] Add Fluent Bit Sidecar for Logging)
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

package com.google.gerrit.k8s.operator.v1beta3.api.model.cluster;

import com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1beta3.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.FluentBitSidecarConfig;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.GerritClusterIngressConfig;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.GlobalRefDbConfig;
import java.util.ArrayList;
import java.util.List;

public class GerritClusterSpec {

  private GerritStorageConfig storage = new GerritStorageConfig();
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritClusterIngressConfig ingress = new GerritClusterIngressConfig();
  private GlobalRefDbConfig refdb = new GlobalRefDbConfig();
  private String serverId = "";
  private List<GerritTemplate> gerrits = new ArrayList<>();
  private ReceiverTemplate receiver;
  private FluentBitSidecarConfig fluentBitSidecar = new FluentBitSidecarConfig();

  public FluentBitSidecarConfig getFluentBitSidecar() {
    return fluentBitSidecar;
  }

  public void setFluentBitSidecar(FluentBitSidecarConfig fluentBitSidecar) {
    this.fluentBitSidecar = fluentBitSidecar;
  }

  public GerritStorageConfig getStorage() {
    return storage;
  }

  public void setStorage(GerritStorageConfig storage) {
    this.storage = storage;
  }

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public GerritClusterIngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(GerritClusterIngressConfig ingress) {
    this.ingress = ingress;
  }

  public GlobalRefDbConfig getRefdb() {
    return refdb;
  }

  public void setRefdb(GlobalRefDbConfig refdb) {
    this.refdb = refdb;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public List<GerritTemplate> getGerrits() {
    return gerrits;
  }

  public void setGerrits(List<GerritTemplate> gerrits) {
    this.gerrits = gerrits;
  }

  public ReceiverTemplate getReceiver() {
    return receiver;
  }

  public void setReceiver(ReceiverTemplate receiver) {
    this.receiver = receiver;
  }
}
=======
>>>>>>> BASE      (1e7a4c [Operator] Change process to create new CRD version)
