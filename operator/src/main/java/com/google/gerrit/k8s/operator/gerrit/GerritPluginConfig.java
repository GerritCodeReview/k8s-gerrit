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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class GerritPluginConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	Set<String> packagedPlugins = new HashSet<>();
	Set<GerritDownloadedPlugin> downloadedPlugins = new HashSet<>();
	GerritPluginCacheConfig cacheConfig;

	public Set<String> getPackagedPlugins() {
		return packagedPlugins;
	}

	public void addPackagedPlugin(String plugin) {
		this.packagedPlugins.add(plugin);
	}

	public Set<GerritDownloadedPlugin> getDownloadedPlugins() {
		return downloadedPlugins;
	}

	public void addDownloadedPlugin(GerritDownloadedPlugin plugin) {
		this.downloadedPlugins.add(plugin);
	}

	public GerritPluginCacheConfig getCacheConfig() {
		return cacheConfig;
	}

	public void setCacheConfig(GerritPluginCacheConfig cacheConfig) {
		this.cacheConfig = cacheConfig;
	}
}