// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.HttpSshServiceConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Deprecated
public class GerritTemplateSpec {
  private String serviceAccount;

  private List<Toleration> tolerations;
  private Affinity affinity;
  private List<TopologySpreadConstraint> topologySpreadConstraints = new ArrayList<>();
  private String priorityClassName;

  private int replicas = 1;
  private int updatePartition = 0;

  private ResourceRequirements resources;

  private GerritProbe startupProbe = new GerritProbe();
  private GerritProbe readinessProbe = new GerritProbe();
  private GerritProbe livenessProbe = new GerritProbe();

  private long gracefulStopTimeout = 30L;

  private HttpSshServiceConfig service = new HttpSshServiceConfig();

  private GerritSite site = new GerritSite();
  private List<GerritPlugin> plugins = List.of();
  private List<GerritModule> libs = List.of();
  private Map<String, String> configFiles = Map.of();
  private String secretRef;
  private GerritMode mode = GerritMode.PRIMARY;

  private GerritDebugConfig debug = new GerritDebugConfig();

  public GerritTemplateSpec() {}

  public GerritTemplateSpec(GerritTemplateSpec templateSpec) {
    this.serviceAccount = templateSpec.serviceAccount;
    this.tolerations = templateSpec.tolerations;
    this.affinity = templateSpec.affinity;
    this.topologySpreadConstraints = templateSpec.topologySpreadConstraints;
    this.priorityClassName = templateSpec.priorityClassName;

    this.replicas = templateSpec.replicas;
    this.updatePartition = templateSpec.updatePartition;

    this.resources = templateSpec.resources;

    this.startupProbe = templateSpec.startupProbe;
    this.readinessProbe = templateSpec.readinessProbe;
    this.livenessProbe = templateSpec.livenessProbe;

    this.gracefulStopTimeout = templateSpec.gracefulStopTimeout;

    this.service = templateSpec.service;

    this.site = templateSpec.site;
    this.plugins = templateSpec.plugins;
    this.libs = templateSpec.libs;
    this.configFiles = templateSpec.configFiles;
    this.secretRef = templateSpec.secretRef;
    this.mode = templateSpec.mode;

    this.debug = templateSpec.debug;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(String serviceAccount) {
    this.serviceAccount = serviceAccount;
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

  public int getUpdatePartition() {
    return updatePartition;
  }

  public void setUpdatePartition(int updatePartition) {
    this.updatePartition = updatePartition;
  }

  public ResourceRequirements getResources() {
    return resources;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public GerritProbe getStartupProbe() {
    return startupProbe;
  }

  public void setStartupProbe(GerritProbe startupProbe) {
    this.startupProbe = startupProbe;
  }

  public GerritProbe getReadinessProbe() {
    return readinessProbe;
  }

  public void setReadinessProbe(GerritProbe readinessProbe) {
    this.readinessProbe = readinessProbe;
  }

  public GerritProbe getLivenessProbe() {
    return livenessProbe;
  }

  public void setLivenessProbe(GerritProbe livenessProbe) {
    this.livenessProbe = livenessProbe;
  }

  public long getGracefulStopTimeout() {
    return gracefulStopTimeout;
  }

  public void setGracefulStopTimeout(long gracefulStopTimeout) {
    this.gracefulStopTimeout = gracefulStopTimeout;
  }

  public HttpSshServiceConfig getService() {
    return service;
  }

  public void setService(HttpSshServiceConfig service) {
    this.service = service;
  }

  public GerritSite getSite() {
    return site;
  }

  public void setSite(GerritSite site) {
    this.site = site;
  }

  public List<GerritPlugin> getPlugins() {
    return plugins;
  }

  public void setPlugins(List<GerritPlugin> plugins) {
    this.plugins = plugins;
  }

  public List<GerritModule> getLibs() {
    return libs;
  }

  /**
   * Gets list of all GerritModules including plugins and lib modules.
   *
   * @return list of all GerritModules including plugins and lib modules
   */
  @JsonIgnore
  public List<GerritModule> getAllGerritModules() {
    return Stream.concat(plugins.stream(), libs.stream()).collect(Collectors.toList());
  }

  public void setLibs(List<GerritModule> libs) {
    this.libs = libs;
  }

  public Map<String, String> getConfigFiles() {
    return configFiles;
  }

  public void setConfigFiles(Map<String, String> configFiles) {
    this.configFiles = configFiles;
  }

  public String getSecretRef() {
    return secretRef;
  }

  public void setSecretRef(String secretRef) {
    this.secretRef = secretRef;
  }

  public GerritMode getMode() {
    return mode;
  }

  public void setMode(GerritMode mode) {
    this.mode = mode;
  }

  public GerritDebugConfig getDebug() {
    return debug;
  }

  public void setDebug(GerritDebugConfig debug) {
    this.debug = debug;
  }

  public enum GerritMode {
    PRIMARY,
    REPLICA
  }

  @JsonIgnore
  public boolean isHighlyAvailablePrimary() {
    return getMode().equals(GerritMode.PRIMARY) && getReplicas() > 1;
  }
}
