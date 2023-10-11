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

package com.google.gerrit.k8s.operator.v1beta1.gerrit.config;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.gerrit.config.ConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.RequiredOption;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.Gerrit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Deprecated
public class HighAvailabilityPluginConfigBuilder extends ConfigBuilder {
  public HighAvailabilityPluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("high-availability.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(new RequiredOption<String>("main", "sharedDirectory", "shared"));
    requiredOptions.add(new RequiredOption<String>("peerInfo", "strategy", "jgroups"));
    requiredOptions.add(new RequiredOption<String>("peerInfo", "jgroups", "myUrl", null));
    requiredOptions.add(
        new RequiredOption<String>("jgroups", "clusterName", gerrit.getMetadata().getName()));
    requiredOptions.add(new RequiredOption<Boolean>("jgroups", "kubernetes", true));
    requiredOptions.add(
        new RequiredOption<String>(
            "jgroups", "kubernetes", "namespace", gerrit.getMetadata().getNamespace()));
    requiredOptions.add(
        new RequiredOption<Set<String>>("jgroups", "kubernetes", "label", getLabels(gerrit)));
    requiredOptions.add(new RequiredOption<Boolean>("cache", "synchronize", true));
    requiredOptions.add(new RequiredOption<Boolean>("event", "synchronize", true));
    requiredOptions.add(new RequiredOption<Boolean>("index", "synchronize", true));
    requiredOptions.add(new RequiredOption<Boolean>("index", "synchronizeForced", true));
    requiredOptions.add(new RequiredOption<Boolean>("healthcheck", "enable", true));
    requiredOptions.add(new RequiredOption<Boolean>("ref-database", "enabled", true));
    return requiredOptions;
  }

  private static Set<String> getLabels(Gerrit gerrit) {
    Map<String, String> selectorLabels =
        GerritStatefulSet.getSelectorLabels(gerrit.getMetadata().getName());
    Set<String> labels = new HashSet<>();
    for (Map.Entry<String, String> label : selectorLabels.entrySet()) {
      labels.add(label.getKey() + "=" + label.getValue());
    }
    return labels;
  }
}
