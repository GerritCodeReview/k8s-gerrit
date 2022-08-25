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

import io.fabric8.kubernetes.api.model.LocalObjectReference;
import java.util.HashSet;
import java.util.Set;

public class GerritClusterSpec {

  private StorageClassConfig storageClasses;
  private SharedStorage gitRepositoryStorage;
  private SharedStorage logsStorage;
  private Set<LocalObjectReference> imagePullSecrets = new HashSet<>();

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

  public Set<LocalObjectReference> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public void setImagePullSecrets(Set<LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }
}
