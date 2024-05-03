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

package com.google.gerrit.k8s.operator.api.model.gerrit;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GerritStatus {
  private boolean ready = false;
  private Map<String, String> appliedConfigMapVersions = new HashMap<>();
  private Map<String, String> appliedSecretVersions = new HashMap<>();

  public boolean isReady() {
    return ready;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  public Map<String, String> getAppliedConfigMapVersions() {
    return appliedConfigMapVersions;
  }

  public void setAppliedConfigMapVersions(Map<String, String> appliedConfigMapVersions) {
    this.appliedConfigMapVersions = appliedConfigMapVersions;
  }

  public Map<String, String> getAppliedSecretVersions() {
    return appliedSecretVersions;
  }

  public void setAppliedSecretVersions(Map<String, String> appliedSecretVersions) {
    this.appliedSecretVersions = appliedSecretVersions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appliedConfigMapVersions, appliedSecretVersions, ready);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritStatus other = (GerritStatus) obj;
    return Objects.equals(appliedConfigMapVersions, other.appliedConfigMapVersions)
        && Objects.equals(appliedSecretVersions, other.appliedSecretVersions)
        && ready == other.ready;
  }

  @Override
  public String toString() {
    return "GerritStatus [ready="
        + ready
        + ", appliedConfigMapVersions="
        + appliedConfigMapVersions
        + ", appliedSecretVersions="
        + appliedSecretVersions
        + "]";
  }
}
