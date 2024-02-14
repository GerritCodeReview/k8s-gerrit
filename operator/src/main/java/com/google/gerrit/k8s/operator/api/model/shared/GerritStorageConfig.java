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

package com.google.gerrit.k8s.operator.api.model.shared;

import java.util.Objects;

public class GerritStorageConfig extends StorageConfig {
  private PluginCacheConfig pluginCache = new PluginCacheConfig();

  public PluginCacheConfig getPluginCache() {
    return pluginCache;
  }

  public void setPluginCache(PluginCacheConfig pluginCache) {
    this.pluginCache = pluginCache;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pluginCache);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritStorageConfig other = (GerritStorageConfig) obj;
    return Objects.equals(pluginCache, other.pluginCache);
  }

  @Override
  public String toString() {
    return "GerritStorageConfig [pluginCache=" + pluginCache + "]";
  }

  public static class PluginCacheConfig {
    private boolean enabled;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public int hashCode() {
      return Objects.hash(enabled);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      PluginCacheConfig other = (PluginCacheConfig) obj;
      return enabled == other.enabled;
    }

    @Override
    public String toString() {
      return "PluginCacheConfig [enabled=" + enabled + "]";
    }
  }
}
