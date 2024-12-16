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

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
import java.util.Objects;

public abstract class GerritMaintenanceTask {
  private String name;
  private String schedule;
  private ResourceRequirements resources = new ResourceRequirements();
  private List<Toleration> tolerations;
  private Affinity affinity;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public ResourceRequirements getResources() {
    return resources;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public List<Toleration> getTolerations() {
    return tolerations;
  }

  public void setTolerations(List<Toleration> tolerations) {
    this.tolerations = tolerations;
  }

  public Affinity getAffinity() {
    return affinity;
  }

  public void setAffinity(Affinity affinity) {
    this.affinity = affinity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(affinity, name, resources, schedule, tolerations);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritMaintenanceTask other = (GerritMaintenanceTask) obj;
    return Objects.equals(affinity, other.affinity)
        && Objects.equals(name, other.name)
        && Objects.equals(resources, other.resources)
        && Objects.equals(schedule, other.schedule)
        && Objects.equals(tolerations, other.tolerations);
  }

  @Override
  public String toString() {
    return "GerritMaintenanceTask [name="
        + name
        + ", schedule="
        + schedule
        + ", resources="
        + resources
        + ", tolerations="
        + tolerations
        + ", affinity="
        + affinity
        + "]";
  }
}
