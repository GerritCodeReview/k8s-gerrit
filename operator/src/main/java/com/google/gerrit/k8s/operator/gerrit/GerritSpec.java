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

package com.google.gerrit.k8s.operator.gerrit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraint;
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass;

public class GerritSpec {
	private String cluster;

	private List<Toleration> tolerations;
	private Affinity affinity;
	private List<TopologySpreadConstraint> topologySpreadConstraints = new ArrayList<>();
	private String priorityClassName;

	private int replicas = 1;
	private int updatePartition = 0;

	private ResourceRequirements resources;

	private Probe startupProbe;
	private Probe readinessProbe;
	private Probe livenessProbe;

	private long gracefulStopTimeout;
	
	private GerritServiceConfig service;

	private GerritSite site = new GerritSite();
	private GerritIndex index = GerritIndex.LUCENE;
	private GerritPluginConfig plugins;
	private Map<String, String> configFiles;
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
	public void setTopologySpreadConstraints(List<TopologySpreadConstraint> topologySpreadConstraints) {
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
	public Probe getStartupProbe() {
		return startupProbe;
	}
	public void setStartupProbe(Probe startupProbe) {
		this.startupProbe = startupProbe;
	}
	public Probe getReadinessProbe() {
		return readinessProbe;
	}
	public void setReadinessProbe(Probe readinessProbe) {
		this.readinessProbe = readinessProbe;
	}
	public Probe getLivenessProbe() {
		return livenessProbe;
	}
	public void setLivenessProbe(Probe livenessProbe) {
		this.livenessProbe = livenessProbe;
	}
	public long getGracefulStopTimeout() {
		return gracefulStopTimeout;
	}
	public void setGracefulStopTimeout(long gracefulStopTimeout) {
		this.gracefulStopTimeout = gracefulStopTimeout;
	}
	public GerritServiceConfig getService() {
		return service;
	}
	public void setService(GerritServiceConfig service) {
		this.service = service;
	}
	public GerritSite getSite() {
		return site;
	}
	public void setSite(GerritSite site) {
		this.site = site;
	}
	public GerritIndex getIndex() {
		return index;
	}
	public void setIndex(GerritIndex index) {
		this.index = index;
	}
	public GerritPluginConfig getPlugins() {
		return plugins;
	}
	public void setPlugins(GerritPluginConfig plugins) {
		this.plugins = plugins;
	}
	public Map<String, String> getConfigFiles() {
		return configFiles;
	}
	public void setConfigFiles(Map<String, String> configFiles) {
		this.configFiles = configFiles;
	}

}
