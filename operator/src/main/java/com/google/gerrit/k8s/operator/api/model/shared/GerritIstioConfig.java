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

package com.google.gerrit.k8s.operator.api.model.shared;

import java.util.Map;
import java.util.Objects;

public class GerritIstioConfig {
  private Map<String, String> gatewaySelector = Map.of("istio", "ingressgateway");

  public Map<String, String> getGatewaySelector() {
    return gatewaySelector;
  }

  public void setGatewaySelector(Map<String, String> gatewaySelector) {
    this.gatewaySelector = gatewaySelector;
  }

  @Override
  public int hashCode() {
    return Objects.hash(gatewaySelector);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritIstioConfig other = (GerritIstioConfig) obj;
    return Objects.equals(gatewaySelector, other.gatewaySelector);
  }

  @Override
  public String toString() {
    return "GerritIstioConfig [gatewaySelector=" + gatewaySelector + "]";
  }
}
