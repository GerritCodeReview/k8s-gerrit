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
import java.util.ArrayList;
import java.util.List;

public class SpannerRefDbPluginConfigBuilder extends ConfigBuilder {
  public SpannerRefDbPluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("spanner-refdb.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(
        new RequiredOption<String>("ref-database", "spanner", "useEmulator", "false"));
    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "spanner",
            "projectName",
            gerrit.getSpec().getRefdb().getSpanner().getProjectName()));
    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database", "spanner", "credentialsPath", "/var/gerrit/etc/gcp-credentials.json"));
    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "spanner",
            "instance",
            gerrit.getSpec().getRefdb().getSpanner().getInstance()));
    requiredOptions.add(
        new RequiredOption<String>(
            "ref-database",
            "spanner",
            "database",
            gerrit.getSpec().getRefdb().getSpanner().getDatabase()));
    return requiredOptions;
  }
}
