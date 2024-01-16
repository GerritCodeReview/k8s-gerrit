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

package com.google.gerrit.k8s.operator.v1beta3.api.model.shared;

public class FluentBitSidecarConfig {
  private String image = "fluent/fluent-bit:latest";
  private String configName = "fluent-bit.conf";
  private String config;
  private String baseConfig =
      """
      [INPUT]
        Name            tail
        Path            /var/mnt/logs/*log
        Tag             <log_name>
        Tag_Regex       var.gerrit.logs.(?<log_name>[^*]+)
      [OUTPUT]
        Name            stdout
        Match           *\n
      """;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getConfigName() {
    return configName;
  }

  public void setConfigName(String configName) {
    this.configName = configName;
  }

  public String getConfig() {
    return baseConfig + config;
  }

  public void setConfig(String config) {
    this.config = config;
  }
}
