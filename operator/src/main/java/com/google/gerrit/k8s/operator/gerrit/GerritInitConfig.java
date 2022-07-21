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

import java.util.Set;

public class GerritInitConfig {
  private String caCertPath = "/var/config/ca.crt";
  private boolean pluginCacheEnabled;
  private String pluginCacheDir = "/var/mnt/plugins";
  private Set<String> packagedPlugins;
  private Set<GerritPlugin> downloadedPlugins;
  private Set<String> installAsLibrary;

  public String getCaCertPath() {
    return caCertPath;
  }

  public void setCaCertPath(String caCertPath) {
    this.caCertPath = caCertPath;
  }

  public boolean isPluginCacheEnabled() {
    return pluginCacheEnabled;
  }

  public void setPluginCacheEnabled(boolean pluginCacheEnabled) {
    this.pluginCacheEnabled = pluginCacheEnabled;
  }

  public String getPluginCacheDir() {
    return pluginCacheDir;
  }

  public void setPluginCacheDir(String pluginCacheDir) {
    this.pluginCacheDir = pluginCacheDir;
  }

  public Set<String> getPackagedPlugins() {
    return packagedPlugins;
  }

  public void setPackagedPlugins(Set<String> packagedPlugins) {
    this.packagedPlugins = packagedPlugins;
  }

  public Set<GerritPlugin> getDownloadedPlugins() {
    return downloadedPlugins;
  }

  public void setDownloadedPlugins(Set<GerritPlugin> downloadedPlugins) {
    this.downloadedPlugins = downloadedPlugins;
  }

  public Set<String> getInstallAsLibrary() {
    return installAsLibrary;
  }

  public void setInstallAsLibrary(Set<String> installAsLibrary) {
    this.installAsLibrary = installAsLibrary;
  }
}
