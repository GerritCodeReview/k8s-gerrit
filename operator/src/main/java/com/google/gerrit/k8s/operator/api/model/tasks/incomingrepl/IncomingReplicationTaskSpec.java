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

import java.util.List;
import java.util.Objects;

public class IncomingReplicationTaskSpec {
  private String schedule;
  private String remote;
  private List<SourceToTargetRepoMapper> repositories;

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String getRemote() {
    return remote;
  }

  public void setRemote(String remote) {
    this.remote = remote;
  }

  public List<SourceToTargetRepoMapper> getRepositories() {
    return repositories;
  }

  public void setRepositories(List<SourceToTargetRepoMapper> repositories) {
    this.repositories = repositories;
  }

  @Override
  public int hashCode() {
    return Objects.hash(schedule, remote, repositories);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncomingReplicationTaskSpec other = (IncomingReplicationTaskSpec) obj;
    return Objects.equals(schedule, other.schedule)
        && Objects.equals(remote, other.remote)
        && Objects.equals(repositories, other.repositories);
  }

  @Override
  public String toString() {
    return "IncomingReplicationTaskSpec [schedule="
        + schedule
        + ", remote="
        + remote
        + ", repositories="
        + repositories
        + "]";
  }
}
