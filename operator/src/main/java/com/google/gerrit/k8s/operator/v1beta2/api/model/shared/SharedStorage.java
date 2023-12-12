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

package com.google.gerrit.k8s.operator.v1beta2.api.model.shared;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Quantity;

@Deprecated
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

  @Deprecated
  public class ExternalPVCConfig {
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
  }
}
