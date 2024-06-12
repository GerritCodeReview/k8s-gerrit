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

package com.google.gerrit.k8s.operator.api.model.indexer;

import java.util.Objects;

public class GerritIndexerVolumeRef {
  protected String persistentVolumeClaim;
  protected String subPath = "indexes";

  public GerritIndexerVolumeRef() {}

  public GerritIndexerVolumeRef(String persistentVolumeClaim, String subPath) {
    this.persistentVolumeClaim = persistentVolumeClaim;
    this.subPath = subPath;
  }

  public String getPersistentVolumeClaim() {
    return persistentVolumeClaim;
  }

  public void setPersistentVolumeClaim(String persistentVolumeClaim) {
    this.persistentVolumeClaim = persistentVolumeClaim;
  }

  public String getSubPath() {
    return subPath;
  }

  public void setSubPath(String subPath) {
    this.subPath = subPath;
  }

  @Override
  public int hashCode() {
    return Objects.hash(persistentVolumeClaim, subPath);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritIndexerVolumeRef other = (GerritIndexerVolumeRef) obj;
    return Objects.equals(persistentVolumeClaim, other.persistentVolumeClaim)
        && Objects.equals(subPath, other.subPath);
  }

  @Override
  public String toString() {
    return "GerritIndexerVolumeRef [persistentVolumeClaim="
        + persistentVolumeClaim
        + ", subPath="
        + subPath
        + "]";
  }
}
