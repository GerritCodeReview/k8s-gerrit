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

public class EventsBrokerConfig {

  private BrokerType brokerType = BrokerType.NONE;
  private KafkaConfig kafkaConfig;

  public KafkaConfig getKafkaConfig() {
    return kafkaConfig;
  }

  public void setKafkaConfig(KafkaConfig kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
  }

  public BrokerType getBrokerType() {
    return brokerType;
  }

  public void setBrokerType(BrokerType brokerType) {
    this.brokerType = brokerType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(brokerType, kafkaConfig);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    EventsBrokerConfig other = (EventsBrokerConfig) obj;
    return brokerType == other.brokerType && Objects.equals(kafkaConfig, other.kafkaConfig);
  }

  @Override
  public String toString() {
    return "EventsBrokerConfig [[brokerType=" + brokerType + ", kafkaConfig=" + kafkaConfig + "]";
  }

  public enum BrokerType {
    NONE,
    KAFKA
  }
}
