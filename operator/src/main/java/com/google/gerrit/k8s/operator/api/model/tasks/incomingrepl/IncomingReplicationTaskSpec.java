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

import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritStorageConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
import java.util.Objects;

public class IncomingReplicationTaskSpec {
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritStorageConfig storage = new GerritStorageConfig();
  private String schedule;
  private IncomingReplicationConfig config;
  private List<SourceToTargetRepoMapper> repositories;
  private ResourceRequirements resources;
  private List<Toleration> tolerations;
  private Affinity affinity;

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public GerritStorageConfig getStorage() {
    return storage;
  }

  public void setStorage(GerritStorageConfig storage) {
    this.storage = storage;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public IncomingReplicationConfig getConfig() {
    return config;
  }

  public void setConfig(IncomingReplicationConfig config) {
    this.config = config;
  }

  public List<SourceToTargetRepoMapper> getRepositories() {
    return repositories;
  }

  public void setRepositories(List<SourceToTargetRepoMapper> repositories) {
    this.repositories = repositories;
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
    return Objects.hash(
        affinity, containerImages, config, repositories, resources, schedule, tolerations);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncomingReplicationTaskSpec other = (IncomingReplicationTaskSpec) obj;
    return Objects.equals(affinity, other.affinity)
        && Objects.equals(containerImages, other.containerImages)
        && Objects.equals(config, other.config)
        && Objects.equals(repositories, other.repositories)
        && Objects.equals(resources, other.resources)
        && Objects.equals(schedule, other.schedule)
        && Objects.equals(tolerations, other.tolerations);
  }

  @Override
  public String toString() {
    return "IncomingReplicationTaskSpec [containerImages="
        + containerImages
        + ", schedule="
        + schedule
        + ", remote="
        + config
        + ", repositories="
        + repositories
        + ", resources="
        + resources
        + ", tolerations="
        + tolerations
        + ", affinity="
        + affinity
        + "]";
  }
}
