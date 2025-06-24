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

package com.google.gerrit.k8s.operator.api.model.shared;

import java.util.Objects;

public class StorageConfig {

  private StorageClassConfig storageClasses = new StorageClassConfig();
  private SharedStorage sharedStorage = new SharedStorage();

  public StorageConfig() {}

  public StorageConfig(GerritStorageConfig gerritStorageConfig) {
    storageClasses = gerritStorageConfig.getStorageClasses();
    sharedStorage = gerritStorageConfig.getSharedStorage();
  }

  public StorageClassConfig getStorageClasses() {
    return storageClasses;
  }

  public void setStorageClasses(StorageClassConfig storageClasses) {
    this.storageClasses = storageClasses;
  }

  public SharedStorage getSharedStorage() {
    return sharedStorage;
  }

  public void setSharedStorage(SharedStorage sharedStorage) {
    this.sharedStorage = sharedStorage;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sharedStorage, storageClasses);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    StorageConfig other = (StorageConfig) obj;
    return Objects.equals(sharedStorage, other.sharedStorage)
        && Objects.equals(storageClasses, other.storageClasses);
  }

  @Override
  public String toString() {
    return "StorageConfig [storageClasses="
        + storageClasses
        + ", sharedStorage="
        + sharedStorage
        + "]";
  }
}
