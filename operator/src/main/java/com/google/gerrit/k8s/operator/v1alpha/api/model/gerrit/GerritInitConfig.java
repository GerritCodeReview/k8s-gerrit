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

package com.google.gerrit.k8s.operator.v1alpha.api.model.gerrit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class GerritInitConfig {
  private String caCertPath = "/var/config/ca.crt";
  private boolean pluginCacheEnabled;
  private String pluginCacheDir = "/var/mnt/plugins";
  private List<GerritPlugin> plugins;
  private List<GerritModule> libs;

  @JsonProperty("highAvailability")
  private boolean isHighlyAvailable;

  private String refdb;

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

  public List<GerritPlugin> getPlugins() {
    return plugins;
  }

  public void setPlugins(List<GerritPlugin> plugins) {
    this.plugins = plugins;
  }

  public List<GerritModule> getLibs() {
    return libs;
  }

  public void setLibs(List<GerritModule> libs) {
    this.libs = libs;
  }

  @JsonProperty("highAvailability")
  public boolean isHighlyAvailable() {
    return isHighlyAvailable;
  }

  @JsonProperty("highAvailability")
  public void setHighlyAvailable(boolean isHighlyAvailable) {
    this.isHighlyAvailable = isHighlyAvailable;
  }

  public String getRefdb() {
    return refdb;
  }

  public void setRefdb(String refdb) {
    this.refdb = refdb;
  }
}
