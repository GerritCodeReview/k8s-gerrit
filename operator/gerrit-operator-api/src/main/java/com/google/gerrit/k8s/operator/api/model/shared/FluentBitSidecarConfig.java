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

package com.google.gerrit.k8s.operator.api.model.shared;

import java.util.Objects;

public class FluentBitSidecarConfig {
  private String image = "fluent/fluent-bit:latest";
  private String config =
      """
      [OUTPUT]
        Name            stdout
        Match           *
      [FILTER]
        Name              modify
        Match             *
        Add k8s.pod.name  ${POD_NAME}\n
      """;

  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void isEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getConfig() {
    return config;
  }

  public void setConfig(String config) {
    this.config = config;
  }

  @Override
  public int hashCode() {
    return Objects.hash(config, enabled, image);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    FluentBitSidecarConfig other = (FluentBitSidecarConfig) obj;
    return Objects.equals(config, other.config)
        && enabled == other.enabled
        && Objects.equals(image, other.image);
  }

  @Override
  public String toString() {
    return "FluentBitSidecarConfig [image="
        + image
        + ", config="
        + config
        + ", enabled="
        + enabled
        + "]";
  }
}
