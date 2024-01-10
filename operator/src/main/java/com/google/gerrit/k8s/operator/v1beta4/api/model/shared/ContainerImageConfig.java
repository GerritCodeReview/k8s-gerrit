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

package com.google.gerrit.k8s.operator.v1beta4.api.model.shared;

import io.fabric8.kubernetes.api.model.LocalObjectReference;
import java.util.HashSet;
import java.util.Set;

public class ContainerImageConfig {
  private String imagePullPolicy = "Always";
  private Set<LocalObjectReference> imagePullSecrets = new HashSet<>();
  private BusyBoxImage busyBox = new BusyBoxImage();
  private GerritRepositoryConfig gerritImages = new GerritRepositoryConfig();

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public Set<LocalObjectReference> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public void setImagePullSecrets(Set<LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  public BusyBoxImage getBusyBox() {
    return busyBox;
  }

  public void setBusyBox(BusyBoxImage busyBox) {
    this.busyBox = busyBox;
  }

  public GerritRepositoryConfig getGerritImages() {
    return gerritImages;
  }

  public void setGerritImages(GerritRepositoryConfig gerritImages) {
    this.gerritImages = gerritImages;
  }
}
