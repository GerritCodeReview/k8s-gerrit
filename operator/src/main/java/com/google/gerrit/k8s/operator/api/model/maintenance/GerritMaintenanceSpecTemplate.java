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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import java.util.Objects;

public class GerritMaintenanceSpecTemplate {
  private GerritProjectsTasks projects = new GerritProjectsTasks();

  public GerritMaintenanceSpecTemplate(GerritMaintenanceSpecTemplate spec) {
    this.projects = spec.getProjects();
  }

  public GerritMaintenanceSpecTemplate() {}

  public GerritProjectsTasks getProjects() {
    return projects;
  }

  public void setProjects(GerritProjectsTasks projects) {
    this.projects = projects;
  }

  @JsonIgnore
  public GerritMaintenanceSpec toGerritMaintenanceSpec(GerritCluster gerritCluster) {
    GerritMaintenanceSpec spec = new GerritMaintenanceSpec(this);
    spec.setContainerImages(gerritCluster.getSpec().getContainerImages());
    spec.setStorage(gerritCluster.getSpec().getStorage());
    return spec;
  }

  @Override
  public int hashCode() {
    return Objects.hash(projects);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritMaintenanceSpecTemplate other = (GerritMaintenanceSpecTemplate) obj;
    return Objects.equals(projects, other.projects);
  }

  @Override
  public String toString() {
    return "GerritMaintenanceSpec [projects=" + projects + "]";
  }
}
