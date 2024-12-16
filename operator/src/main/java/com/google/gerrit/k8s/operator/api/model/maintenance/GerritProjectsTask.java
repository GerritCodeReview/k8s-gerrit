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

package com.google.gerrit.k8s.operator.api.model.maintenance;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class GerritProjectsTask extends GerritMaintenanceTask {
  private Set<String> include = new HashSet<>();
  private Set<String> exclude = new HashSet<>();

  public Set<String> getInclude() {
    return include;
  }

  public void setInclude(Set<String> include) {
    this.include = include;
  }

  public Set<String> getExclude() {
    return exclude;
  }

  public void setExclude(Set<String> exclude) {
    this.exclude = exclude;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(exclude, include);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    GerritProjectsTask other = (GerritProjectsTask) obj;
    return Objects.equals(exclude, other.exclude) && Objects.equals(include, other.include);
  }

  @Override
  public String toString() {
    return "GerritProjectsTask [include=" + include + ", exclude=" + exclude + "]";
  }
}
