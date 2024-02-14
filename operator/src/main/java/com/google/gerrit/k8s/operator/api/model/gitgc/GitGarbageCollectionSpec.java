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

package com.google.gerrit.k8s.operator.api.model.gitgc;

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
  private boolean disableBitmapIndex;
  private boolean disablePackRefs;
  private boolean preservePacks;
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

  public boolean isDisableBitmapIndex() {
    return disableBitmapIndex;
  }

  public void setDisableBitmapIndex(boolean disableBitmapIndex) {
    this.disableBitmapIndex = disableBitmapIndex;
  }

  public boolean isDisablePackRefs() {
    return disablePackRefs;
  }

  public void setDisablePackRefs(boolean disablePackRefs) {
    this.disablePackRefs = disablePackRefs;
  }

  public boolean isPreservePacks() {
    return preservePacks;
  }

  public void setPreservePacks(boolean preservePacks) {
    this.preservePacks = preservePacks;
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
    return Objects.hash(
        affinity,
        cluster,
        disableBitmapIndex,
        disablePackRefs,
        preservePacks,
        projects,
        resources,
        schedule,
        tolerations);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GitGarbageCollectionSpec other = (GitGarbageCollectionSpec) obj;
    return Objects.equals(affinity, other.affinity)
        && Objects.equals(cluster, other.cluster)
        && disableBitmapIndex == other.disableBitmapIndex
        && disablePackRefs == other.disablePackRefs
        && preservePacks == other.preservePacks
        && Objects.equals(projects, other.projects)
        && Objects.equals(resources, other.resources)
        && Objects.equals(schedule, other.schedule)
        && Objects.equals(tolerations, other.tolerations);
  }

  @Override
  public String toString() {
    return "GitGarbageCollectionSpec [cluster="
        + cluster
        + ", schedule="
        + schedule
        + ", projects="
        + projects
        + ", disableBitmapIndex="
        + disableBitmapIndex
        + ", disablePackRefs="
        + disablePackRefs
        + ", preservePacks="
        + preservePacks
        + ", resources="
        + resources
        + ", tolerations="
        + tolerations
        + ", affinity="
        + affinity
        + "]";
  }
}
