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

package com.google.gerrit.k8s.operator.v1beta1.api.model.gitgc;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GitGarbageCollectionSpec {
  private String cluster;
  private String schedule;
  private Set<String> projects;
  private ResourceRequirements resources;
  private List<Toleration> tolerations;
  private Affinity affinity;

  public GitGarbageCollectionSpec() {
    resources = new ResourceRequirements();
    projects = new HashSet<>();
  }

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String getSchedule() {
    return schedule;
  }

  public Set<String> getProjects() {
    return projects;
  }

  public void setProjects(Set<String> projects) {
    this.projects = projects;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public ResourceRequirements getResources() {
    return resources;
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
    return Objects.hash(cluster, projects, resources, schedule);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GitGarbageCollectionSpec) {
      GitGarbageCollectionSpec other = (GitGarbageCollectionSpec) obj;
      return Objects.equals(cluster, other.cluster)
          && Objects.equals(projects, other.projects)
          && Objects.equals(resources, other.resources)
          && Objects.equals(schedule, other.schedule);
    }
    return false;
  }
}
