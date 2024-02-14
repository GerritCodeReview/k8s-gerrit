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

package com.google.gerrit.k8s.operator.api.model.gerrit;

import java.util.Objects;

public class GerritModuleData {
  private String secretRef;

  public String getSecretRef() {
    return secretRef;
  }

  public void setSecretRef(String secretRef) {
    this.secretRef = secretRef;
  }

  @Override
  public int hashCode() {
    return Objects.hash(secretRef);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritModuleData other = (GerritModuleData) obj;
    return Objects.equals(secretRef, other.secretRef);
  }

  @Override
  public String toString() {
    return "GerritModuleData [secretRef=" + secretRef + "]";
  }
}
