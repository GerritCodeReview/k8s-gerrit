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

package com.google.gerrit.k8s.operator.v1beta3.api.model.gitgc;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Deprecated
public class GitGarbageCollectionStatus {
  private boolean replicateAll = false;
  private Set<String> excludedProjects = new HashSet<>();
  private GitGcState state = GitGcState.INACTIVE;

  public boolean isReplicateAll() {
    return replicateAll;
  }

  public void setReplicateAll(boolean replicateAll) {
    this.replicateAll = replicateAll;
  }

  public Set<String> getExcludedProjects() {
    return excludedProjects;
  }

  public void resetExcludedProjects() {
    excludedProjects = new HashSet<>();
  }

  public void excludeProjects(Set<String> projects) {
    excludedProjects.addAll(projects);
  }

  public GitGcState getState() {
    return state;
  }

  public void setState(GitGcState state) {
    this.state = state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(excludedProjects, replicateAll, state);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GitGarbageCollectionStatus) {
      GitGarbageCollectionStatus other = (GitGarbageCollectionStatus) obj;
      return Objects.equals(excludedProjects, other.excludedProjects)
          && replicateAll == other.replicateAll
          && state == other.state;
    }
    return false;
  }

  public enum GitGcState {
    ACTIVE,
    INACTIVE,
    CONFLICT,
    ERROR
  }
}
