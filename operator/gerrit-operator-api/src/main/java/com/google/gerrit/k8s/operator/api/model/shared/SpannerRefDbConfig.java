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

import java.util.Objects;

public class SpannerRefDbConfig {
  private String projectName;
  private String instance;
  private String database;

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getInstance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  @Override
  public int hashCode() {
    return Objects.hash(database, instance, projectName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SpannerRefDbConfig other = (SpannerRefDbConfig) obj;
    return Objects.equals(database, other.database)
        && Objects.equals(instance, other.instance)
        && Objects.equals(projectName, other.projectName);
  }

  @Override
  public String toString() {
    return "SpannerRefDbConfig [projectName="
        + projectName
        + ", instance="
        + instance
        + ", database="
        + database
        + "]";
  }
}
