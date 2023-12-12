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

package com.google.gerrit.k8s.operator.v1beta2.api.model.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Deprecated
public class GerritRepositoryConfig {
  private String registry;
  private String org;
  private String tag;

  public GerritRepositoryConfig() {
    this.registry = "docker.io";
    this.org = "k8sgerrit";
    this.tag = "latest";
  }

  public void setRegistry(String registry) {
    this.registry = registry;
  }

  public String getRegistry() {
    return registry;
  }

  public String getOrg() {
    return org;
  }

  public void setOrg(String org) {
    this.org = org;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getTag() {
    return tag;
  }

  @JsonIgnore
  public String getFullImageName(String image) {
    StringBuilder builder = new StringBuilder();

    if (registry != null) {
      builder.append(registry);
      builder.append("/");
    }

    if (org != null) {
      builder.append(org);
      builder.append("/");
    }

    builder.append(image);

    if (tag != null) {
      builder.append(":");
      builder.append(tag);
    }

    return builder.toString();
  }
}
