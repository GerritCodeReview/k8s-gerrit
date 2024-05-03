// Copyright (C) 2022 The Android Open Source Project
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

public class BusyBoxImage {
  private String registry;
  private String tag;

  public BusyBoxImage() {
    this.registry = "docker.io";
    this.tag = "latest";
  }

  public void setRegistry(String registry) {
    this.registry = registry;
  }

  public String getRegistry() {
    return registry;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getTag() {
    return tag;
  }

  @JsonIgnore
  public String getBusyBoxImage() {
    StringBuilder builder = new StringBuilder();

    if (registry != null) {
      builder.append(registry);
      builder.append("/");
    }

    builder.append("busybox");

    if (tag != null) {
      builder.append(":");
      builder.append(tag);
    }

    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(registry, tag);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    BusyBoxImage other = (BusyBoxImage) obj;
    return Objects.equals(registry, other.registry) && Objects.equals(tag, other.tag);
  }

  @Override
  public String toString() {
    return "BusyBoxImage [registry=" + registry + ", tag=" + tag + "]";
  }
}
