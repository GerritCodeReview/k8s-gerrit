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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

public class GerritIndexerInputVolumeRef extends GerritIndexerVolumeRef {
  private boolean createSnapshot = true;

  public GerritIndexerInputVolumeRef(
      boolean createSnapshot, String persistentVolumeClaim, String subPath) {
    super(persistentVolumeClaim, subPath);
    this.createSnapshot = createSnapshot;
  }

  @JsonIgnore
  static GerritIndexerInputVolumeRef defaultForSiteVolume() {
    return new GerritIndexerInputVolumeRef(true, null, null);
  }

  @JsonIgnore
  static GerritIndexerInputVolumeRef defaultForRepositoriesVolume() {
    return new GerritIndexerInputVolumeRef(false, null, "git");
  }

  public boolean isCreateSnapshot() {
    return createSnapshot;
  }

  public void setCreateSnapshot(boolean createSnapshot) {
    this.createSnapshot = createSnapshot;
  }

  @Override
  public int hashCode() {
    return Objects.hash(createSnapshot, persistentVolumeClaim, subPath);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritIndexerInputVolumeRef other = (GerritIndexerInputVolumeRef) obj;
    return createSnapshot == other.createSnapshot
        && Objects.equals(persistentVolumeClaim, other.persistentVolumeClaim)
        && Objects.equals(subPath, other.subPath);
  }

  @Override
  public String toString() {
    return "GerritIndexerVolumeRef [createSnapshot="
        + createSnapshot
        + ", persistentVolumeClaim="
        + persistentVolumeClaim
        + ", subPath="
        + subPath
        + "]";
  }
}
