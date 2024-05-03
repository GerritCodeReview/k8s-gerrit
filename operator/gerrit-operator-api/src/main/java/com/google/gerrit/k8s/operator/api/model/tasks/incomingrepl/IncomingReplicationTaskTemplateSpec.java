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

import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IncomingReplicationTaskTemplateSpec {
  @Required private String schedule;
  @Required private IncomingReplicationConfig config;
  private ResourceRequirements resources;
  private List<Toleration> tolerations = new ArrayList<>();
  private Affinity affinity;
  private String secretRef;

  public IncomingReplicationTaskTemplateSpec() {}

  public IncomingReplicationTaskTemplateSpec(IncomingReplicationTaskTemplateSpec spec) {
    this.schedule = spec.getSchedule();
    this.config = spec.getConfig();
    this.resources = spec.getResources();
    this.tolerations = spec.getTolerations();
    this.affinity = spec.getAffinity();
    this.secretRef = spec.getSecretRef();
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

  public String getSecretRef() {
    return secretRef;
  }

  public void setSecretRef(String secretRef) {
    this.secretRef = secretRef;
  }

  @Override
  public int hashCode() {
    return Objects.hash(affinity, config, resources, schedule, tolerations, secretRef);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncomingReplicationTaskTemplateSpec other = (IncomingReplicationTaskTemplateSpec) obj;
    return Objects.equals(affinity, other.affinity)
        && Objects.equals(config, other.config)
        && Objects.equals(resources, other.resources)
        && Objects.equals(schedule, other.schedule)
        && Objects.equals(tolerations, other.tolerations)
        && Objects.equals(secretRef, other.secretRef);
  }

  @Override
  public String toString() {
    return "IncomingReplicationTaskSpec [schedule="
        + schedule
        + ", remote="
        + config
        + ", resources="
        + resources
        + ", tolerations="
        + tolerations
        + ", affinity="
        + affinity
        + ", secretRef="
        + secretRef
        + "]";
  }
}
