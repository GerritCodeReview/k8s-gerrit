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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IncomingReplicationConfig {
  private List<Remote> remotes = new ArrayList<>();

  public List<Remote> getRemotes() {
    return remotes;
  }

  public void setRemotes(List<Remote> remotes) {
    this.remotes = remotes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(remotes);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncomingReplicationConfig other = (IncomingReplicationConfig) obj;
    return Objects.equals(remotes, other.remotes);
  }

  @Override
  public String toString() {
    return "IncomingReplicationConfig [remotes=" + remotes + "]";
  }
}
