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

package com.google.gerrit.k8s.operator.api.model.maintenance;

import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritStorageConfig;
import java.util.Objects;

public class GerritMaintenanceSpec extends GerritMaintenanceSpecTemplate {
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritStorageConfig storage = new GerritStorageConfig();

  public GerritMaintenanceSpec() {}

  public GerritMaintenanceSpec(GerritMaintenanceSpecTemplate template) {
    super(template);
  }

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public GerritStorageConfig getStorage() {
    return storage;
  }

  public void setStorage(GerritStorageConfig storage) {
    this.storage = storage;
  }

  @Override
  public int hashCode() {
    return Objects.hash(containerImages, storage);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritMaintenanceSpec other = (GerritMaintenanceSpec) obj;
    return Objects.equals(containerImages, other.containerImages)
        && Objects.equals(storage, other.storage);
  }

  @Override
  public String toString() {
    return "GerritMaintenanceSpec [containerImages="
        + containerImages
        + ", storage="
        + storage
        + "]";
  }
}
