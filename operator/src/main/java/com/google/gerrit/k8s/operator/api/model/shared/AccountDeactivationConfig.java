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

/* TODO:
 * There should be an account deletion task reconciler and an account deletion task custom resource which is
 * created similar to how we do it with Gerrit
 *
 */

package com.google.gerrit.k8s.operator.api.model.shared;

import java.util.Objects;

public class AccountDeactivationConfig {
  private String schedule = "";
  private String credentialSecretRef = "";
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void isEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String getCredentialSecretRef() {
    return credentialSecretRef;
  }

  public void setCredentialSecretRef(String credentialSecretRef) {
    this.credentialSecretRef = credentialSecretRef;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, schedule, credentialSecretRef);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    AccountDeactivationConfig other = (AccountDeactivationConfig) obj;
    return Objects.equals(schedule, other.schedule)
        && enabled == other.enabled
        && Objects.equals(credentialSecretRef, other.credentialSecretRef);
  }

  @Override
  public String toString() {
    return "AccountDeactivationConfig [schedule="
        + schedule
        + ", credentialSecretRef="
        + credentialSecretRef
        + ", enabled="
        + enabled
        + "]";
  }
}
