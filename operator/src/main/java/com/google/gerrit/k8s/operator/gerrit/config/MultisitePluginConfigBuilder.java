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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultisitePluginConfigBuilder extends ConfigBuilder {

  private static final String BROKER = "broker";
  private static final Map<String, String> TOPIC_NAMES =
      Map.of(
          "indexEventTopic", "index_event",
          "batchIndexEventTopic", "batch_index",
          "streamEventTopic", "stream_event",
          "projectListEventTopic", "list_project",
          "cacheEventTopic", "cache_eviction");

  public MultisitePluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("multi-site.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    String serverId = gerrit.getSpec().getServerId();
    return TOPIC_NAMES.entrySet().stream()
        .map(
            entry ->
                new RequiredOption<String>(
                    BROKER, entry.getKey(), entry.getValue() + "_" + serverId))
        .collect(Collectors.toList());
  }
}
