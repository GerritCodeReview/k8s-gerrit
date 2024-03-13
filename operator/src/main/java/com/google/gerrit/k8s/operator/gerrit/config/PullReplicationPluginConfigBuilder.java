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

package com.google.gerrit.k8s.operator.gerrit.config;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import java.util.ArrayList;
import java.util.List;

public class PullReplicationPluginConfigBuilder extends ConfigBuilder {

  public PullReplicationPluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("replication.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(
        new RequiredOption<String>(
            "replication", "eventbrokertopic", "stream_event_" + gerrit.getSpec().getServerId()));
    requiredOptions.add(new RequiredOption<Boolean>("replication", "consumeStreamEvents", false));
    return requiredOptions;
  }
}
