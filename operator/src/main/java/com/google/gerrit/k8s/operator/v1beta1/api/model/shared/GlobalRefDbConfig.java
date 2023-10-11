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

package com.google.gerrit.k8s.operator.v1beta1.api.model.shared;

@Deprecated
public class GlobalRefDbConfig {
  private RefDatabase database = RefDatabase.NONE;
  private ZookeeperRefDbConfig zookeeper;
  private SpannerRefDbConfig spanner;

  public RefDatabase getDatabase() {
    return database;
  }

  public void setDatabase(RefDatabase database) {
    this.database = database;
  }

  public ZookeeperRefDbConfig getZookeeper() {
    return zookeeper;
  }

  public void setZookeeper(ZookeeperRefDbConfig zookeeper) {
    this.zookeeper = zookeeper;
  }

  public SpannerRefDbConfig getSpanner() {
    return spanner;
  }

  public void setSpanner(SpannerRefDbConfig spanner) {
    this.spanner = spanner;
  }

  public enum RefDatabase {
    NONE,
    ZOOKEEPER,
    SPANNER,
  }
}
