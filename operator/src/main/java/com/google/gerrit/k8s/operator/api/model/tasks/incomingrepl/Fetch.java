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

package com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl;

import io.fabric8.generator.annotation.Required;
import java.util.Objects;

public class Fetch {
  @Required private String remoteRepo;
  private String localRepo;
  private String refSpec;

  public String getRemoteRepo() {
    return remoteRepo;
  }

  public void setRemoteRepo(String remoteRepo) {
    this.remoteRepo = remoteRepo;
  }

  public String getLocalRepo() {
    return localRepo;
  }

  public void setLocalRepo(String localRepo) {
    this.localRepo = localRepo;
  }

  public String getRefSpec() {
    return refSpec;
  }

  public void setRefSpec(String refSpec) {
    this.refSpec = refSpec;
  }

  @Override
  public int hashCode() {
    return Objects.hash(localRepo, refSpec, remoteRepo);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Fetch other = (Fetch) obj;
    return Objects.equals(localRepo, other.localRepo)
        && Objects.equals(refSpec, other.refSpec)
        && Objects.equals(remoteRepo, other.remoteRepo);
  }

  @Override
  public String toString() {
    return "Fetch [remoteRepo="
        + remoteRepo
        + ", localRepo="
        + localRepo
        + ", refSpec="
        + refSpec
        + "]";
  }
}
