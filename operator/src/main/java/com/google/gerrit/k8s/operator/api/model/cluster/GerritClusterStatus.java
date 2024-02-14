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

package com.google.gerrit.k8s.operator.api.model.cluster;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GerritClusterStatus {

  private Map<String, List<String>> members;

  public Map<String, List<String>> getMembers() {
    return members;
  }

  public void setMembers(Map<String, List<String>> members) {
    this.members = members;
  }

  @Override
  public int hashCode() {
    return Objects.hash(members);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritClusterStatus other = (GerritClusterStatus) obj;
    return Objects.equals(members, other.members);
  }

  @Override
  public String toString() {
    return "GerritClusterStatus [members=" + members + "]";
  }
}
