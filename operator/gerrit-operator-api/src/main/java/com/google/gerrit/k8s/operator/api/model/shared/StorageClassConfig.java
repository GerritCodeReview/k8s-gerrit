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

import java.util.Objects;

public class StorageClassConfig {

  String readWriteOnce = "default";
  String readWriteMany = "shared-storage";
  NfsWorkaroundConfig nfsWorkaround = new NfsWorkaroundConfig();

  public String getReadWriteOnce() {
    return readWriteOnce;
  }

  public String getReadWriteMany() {
    return readWriteMany;
  }

  public void setReadWriteOnce(String readWriteOnce) {
    this.readWriteOnce = readWriteOnce;
  }

  public void setReadWriteMany(String readWriteMany) {
    this.readWriteMany = readWriteMany;
  }

  public NfsWorkaroundConfig getNfsWorkaround() {
    return nfsWorkaround;
  }

  public void setNfsWorkaround(NfsWorkaroundConfig nfsWorkaround) {
    this.nfsWorkaround = nfsWorkaround;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nfsWorkaround, readWriteMany, readWriteOnce);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    StorageClassConfig other = (StorageClassConfig) obj;
    return Objects.equals(nfsWorkaround, other.nfsWorkaround)
        && Objects.equals(readWriteMany, other.readWriteMany)
        && Objects.equals(readWriteOnce, other.readWriteOnce);
  }

  @Override
  public String toString() {
    return "StorageClassConfig [readWriteOnce="
        + readWriteOnce
        + ", readWriteMany="
        + readWriteMany
        + ", nfsWorkaround="
        + nfsWorkaround
        + "]";
  }
}
