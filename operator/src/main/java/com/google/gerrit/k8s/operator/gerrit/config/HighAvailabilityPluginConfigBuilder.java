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

package com.google.gerrit.k8s.operator.gerrit.config;

import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;

public class HighAvailabilityPluginConfigBuilder extends PluginConfigBuilder {
  public HighAvailabilityPluginConfigBuilder() {
    super("high-availability");
  }

  @Override
  void addRequiredOptions(Gerrit gerrit) {
    addRequiredOption(
        new RequiredPluginOption<String>("high-availability", "main", "sharedDirectory", "shared"));
    addRequiredOption(
        new RequiredPluginOption<String>("high-availability", "peerInfo", "strategy", "jgroups"));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "high-availability", "peerInfo", "jgroups", "myUrl", null));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "high-availability", "jgroups", "clusterName", gerrit.getMetadata().getName()));
    addRequiredOption(
        new RequiredPluginOption<Boolean>("high-availability", "cache", "synchronize", true));
    addRequiredOption(
        new RequiredPluginOption<Boolean>("high-availability", "event", "synchronize", true));
    addRequiredOption(
        new RequiredPluginOption<Boolean>("high-availability", "index", "synchronize", true));
    addRequiredOption(
        new RequiredPluginOption<Boolean>("high-availability", "index", "synchronizeForced", true));
    addRequiredOption(
        new RequiredPluginOption<Boolean>("high-availability", "healthcheck", "enable", true));
  }
}
