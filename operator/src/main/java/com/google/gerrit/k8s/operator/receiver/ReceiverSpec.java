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

package com.google.gerrit.k8s.operator.receiver;

import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberSpec;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraint;
import java.util.ArrayList;
import java.util.List;

public class ReceiverSpec implements GerritClusterMemberSpec {
  private String cluster;

  private List<Toleration> tolerations;
  private Affinity affinity;
  private List<TopologySpreadConstraint> topologySpreadConstraints = new ArrayList<>();
  private String priorityClassName;

  private int replicas = 1;
  private IntOrString maxSurge = new IntOrString(1);
  private IntOrString maxUnavailable = new IntOrString(1);

  private ResourceRequirements resources;

  private ReceiverProbe readinessProbe = new ReceiverProbe();
  private ReceiverProbe livenessProbe = new ReceiverProbe();

  private ReceiverServiceConfig service = new ReceiverServiceConfig();

  private String credentialSecretRef;

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
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

  public List<TopologySpreadConstraint> getTopologySpreadConstraints() {
    return topologySpreadConstraints;
  }

  public void setTopologySpreadConstraints(
      List<TopologySpreadConstraint> topologySpreadConstraints) {
    this.topologySpreadConstraints = topologySpreadConstraints;
  }

  public String getPriorityClassName() {
    return priorityClassName;
  }

  public void setPriorityClassName(String priorityClassName) {
    this.priorityClassName = priorityClassName;
  }

  public int getReplicas() {
    return replicas;
  }

  public void setReplicas(int replicas) {
    this.replicas = replicas;
  }

  public IntOrString getMaxSurge() {
    return maxSurge;
  }

  public void setMaxSurge(IntOrString maxSurge) {
    this.maxSurge = maxSurge;
  }

  public IntOrString getMaxUnavailable() {
    return maxUnavailable;
  }

  public void setMaxUnavailable(IntOrString maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  public ResourceRequirements getResources() {
    return resources;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public ReceiverProbe getReadinessProbe() {
    return readinessProbe;
  }

  public void setReadinessProbe(ReceiverProbe readinessProbe) {
    this.readinessProbe = readinessProbe;
  }

  public ReceiverProbe getLivenessProbe() {
    return livenessProbe;
  }

  public void setLivenessProbe(ReceiverProbe livenessProbe) {
    this.livenessProbe = livenessProbe;
  }

  public ReceiverServiceConfig getService() {
    return service;
  }

  public void setService(ReceiverServiceConfig service) {
    this.service = service;
  }

  public String getCredentialSecretRef() {
    return credentialSecretRef;
  }

  public void setCredentialSecretRef(String credentialSecretRef) {
    this.credentialSecretRef = credentialSecretRef;
  }
}
