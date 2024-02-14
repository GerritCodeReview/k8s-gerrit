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

package com.google.gerrit.k8s.operator.api.model.shared;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.Objects;

public class SharedStorage {
  private ExternalPVCConfig externalPVC = new ExternalPVCConfig();
  private Quantity size;
  private String volumeName;
  private LabelSelector selector;

  public ExternalPVCConfig getExternalPVC() {
    return externalPVC;
  }

  public void setExternalPVC(ExternalPVCConfig externalPVC) {
    this.externalPVC = externalPVC;
  }

  public Quantity getSize() {
    return size;
  }

  public String getVolumeName() {
    return volumeName;
  }

  public void setSize(Quantity size) {
    this.size = size;
  }

  public void setVolumeName(String volumeName) {
    this.volumeName = volumeName;
  }

  public LabelSelector getSelector() {
    return selector;
  }

  public void setSelector(LabelSelector selector) {
    this.selector = selector;
  }

  @Override
  public int hashCode() {
    return Objects.hash(externalPVC, selector, size, volumeName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SharedStorage other = (SharedStorage) obj;
    return Objects.equals(externalPVC, other.externalPVC)
        && Objects.equals(selector, other.selector)
        && Objects.equals(size, other.size)
        && Objects.equals(volumeName, other.volumeName);
  }

  @Override
  public String toString() {
    return "SharedStorage [externalPVC="
        + externalPVC
        + ", size="
        + size
        + ", volumeName="
        + volumeName
        + ", selector="
        + selector
        + "]";
  }

  public static class ExternalPVCConfig {
    private boolean enabled;
    private String claimName = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getClaimName() {
      return claimName;
    }

    public void setClaimName(String claimName) {
      this.claimName = claimName;
    }

    @Override
    public int hashCode() {
      return Objects.hash(claimName, enabled);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      ExternalPVCConfig other = (ExternalPVCConfig) obj;
      return Objects.equals(claimName, other.claimName) && enabled == other.enabled;
    }

    @Override
    public String toString() {
      return "ExternalPVCConfig [enabled=" + enabled + ", claimName=" + claimName + "]";
    }
  }
}
