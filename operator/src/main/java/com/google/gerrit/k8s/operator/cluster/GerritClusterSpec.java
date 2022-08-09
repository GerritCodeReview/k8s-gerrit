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

package com.google.gerrit.k8s.operator.cluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GerritClusterSpec {

  private StorageClassConfig storageClasses;
  private SharedStorage gitRepositoryStorage;
  private SharedStorage logsStorage;
  private String imagePullPolicy;
  private Set<String> imagePullSecrets = new HashSet<>();
  private BusyBoxImage busyBox = new BusyBoxImage();

  public StorageClassConfig getStorageClasses() {
    return storageClasses;
  }

  public SharedStorage getGitRepositoryStorage() {
    return gitRepositoryStorage;
  }

  public void setStorageClasses(StorageClassConfig storageClasses) {
    this.storageClasses = storageClasses;
  }

  public void setGitRepositoryStorage(SharedStorage gitRepositoryStorage) {
    this.gitRepositoryStorage = gitRepositoryStorage;
  }

  public SharedStorage getLogsStorage() {
    return logsStorage;
  }

  public void setLogsStorage(SharedStorage logsStorage) {
    this.logsStorage = logsStorage;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public Set<LocalObjectReference> getImagePullSecrets() {
    return this.imagePullSecrets.stream()
        .map(sec -> new LocalObjectReference(sec))
        .collect(Collectors.toSet());
  }

  public void setImagePullSecrets(Set<String> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  public BusyBoxImage getBusyBox() {
    return busyBox;
  }

  public void setBusyBox(BusyBoxImage busyBox) {
    this.busyBox = busyBox;
  }
}
