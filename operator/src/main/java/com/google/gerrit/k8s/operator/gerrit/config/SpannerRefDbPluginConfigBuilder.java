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

public class SpannerRefDbPluginConfigBuilder extends PluginConfigBuilder {
  public static final String SPANNER_CREDENTIALS_FILE = "gcp-credentials.json";

  public SpannerRefDbPluginConfigBuilder() {
    super("spanner-refdb");
  }

  @Override
  void addRequiredOptions(Gerrit gerrit) {
    addRequiredOption(
        new RequiredPluginOption<String>(
            "spanner-refdb", "ref-database", "spanner", "useEmulator", "false"));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "spanner-refdb",
            "ref-database",
            "spanner",
            "projectName",
            gerrit.getSpec().getRefdb().getSpanner().getProjectName()));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "spanner-refdb",
            "ref-database",
            "spanner",
            "credentialsPath",
            "etc/" + SPANNER_CREDENTIALS_FILE));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "spanner-refdb",
            "ref-database",
            "spanner",
            "instance",
            gerrit.getSpec().getRefdb().getSpanner().getInstance()));
    addRequiredOption(
        new RequiredPluginOption<String>(
            "spanner-refdb",
            "ref-database",
            "spanner",
            "database",
            gerrit.getSpec().getRefdb().getSpanner().getDatabase()));
  }
}
