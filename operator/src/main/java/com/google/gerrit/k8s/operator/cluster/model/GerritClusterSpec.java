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

package com.google.gerrit.k8s.operator.cluster.model;

import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.shared.model.ContainerImageConfig;
import com.google.gerrit.k8s.operator.shared.model.GerritStorageConfig;
import java.util.ArrayList;
import java.util.List;

public class GerritClusterSpec {

  private GerritStorageConfig storage = new GerritStorageConfig();
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritClusterIngressConfig ingress = new GerritClusterIngressConfig();
  private List<GerritTemplate> gerrits = new ArrayList<>();

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

  public List<GerritTemplate> getGerrits() {
    return gerrits;
  }

  public void setGerrits(List<GerritTemplate> gerrits) {
    this.gerrits = gerrits;
  }
}
