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

import java.util.Map;
import java.util.Objects;

public class EventsKafkaConfig {
  private Map<String, String> kafkaProperties = Map.of();

  private Map<String, String> pluginProperties = Map.of();

  public Map<String, String> getKafkaProperties() {
    return kafkaProperties;
  }

  public void setKafkaProperties(Map<String, String> kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  public Map<String, String> getPluginProperties() {
    return pluginProperties;
  }

  public void setPluginProperties(Map<String, String> pluginProperties) {
    this.pluginProperties = pluginProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventsKafkaConfig that = (EventsKafkaConfig) o;
    return Objects.equals(kafkaProperties, that.kafkaProperties)
        && Objects.equals(pluginProperties, that.pluginProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kafkaProperties, pluginProperties);
  }

  @Override
  public String toString() {
    return "EventsKafkaConfig{"
        + "kafkaProperties="
        + kafkaProperties
        + ", pluginProperties="
        + pluginProperties
        + '}';
  }
}
