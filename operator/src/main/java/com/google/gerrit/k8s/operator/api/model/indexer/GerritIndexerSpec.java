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

package com.google.gerrit.k8s.operator.api.model.indexer;

import com.google.gerrit.k8s.operator.api.model.shared.IndexConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GerritIndexerSpec {
  private String cluster;
  private List<Toleration> tolerations = new ArrayList<>();
  private Affinity affinity;
  private ResourceRequirements resources = new ResourceRequirements();
  private Map<String, String> configFiles = new HashMap<>();
  private GerritIndexerStorage storage = new GerritIndexerStorage();
  private IndexConfig index;

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

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public ResourceRequirements getResources() {
    return resources;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public Map<String, String> getConfigFiles() {
    return configFiles;
  }

  public void setConfigFiles(Map<String, String> configFiles) {
    this.configFiles = configFiles;
  }

  public GerritIndexerStorage getStorage() {
    return storage;
  }

  public void setStorage(GerritIndexerStorage storage) {
    this.storage = storage;
  }

  public IndexConfig getIndex() {
    return index;
  }

  public void setIndex(IndexConfig index) {
    this.index = index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(affinity, cluster, configFiles, index, resources, storage, tolerations);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritIndexerSpec other = (GerritIndexerSpec) obj;
    return Objects.equals(affinity, other.affinity)
        && Objects.equals(cluster, other.cluster)
        && Objects.equals(configFiles, other.configFiles)
        && Objects.equals(index, other.index)
        && Objects.equals(resources, other.resources)
        && Objects.equals(storage, other.storage)
        && Objects.equals(tolerations, other.tolerations);
  }

  @Override
  public String toString() {
    return "GerritIndexerSpec [cluster="
        + cluster
        + ", tolerations="
        + tolerations
        + ", affinity="
        + affinity
        + ", resources="
        + resources
        + ", configFiles="
        + configFiles
        + ", storage="
        + storage
        + ", index="
        + index
        + "]";
  }
}
