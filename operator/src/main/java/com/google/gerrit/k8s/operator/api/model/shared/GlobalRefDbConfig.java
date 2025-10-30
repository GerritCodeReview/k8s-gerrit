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

public class GlobalRefDbConfig {
  private RefDatabase database = RefDatabase.NONE;
  private ZookeeperRefDbConfig zookeeper;
  private SpannerRefDbConfig spanner;
  private DynamoRefDbConfig dynamoDb;

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

  public DynamoRefDbConfig getDynamoDb() {
    return dynamoDb;
  }

  public void setDynamoDb(DynamoRefDbConfig dynamoDb) {
    this.dynamoDb = dynamoDb;
  }

  @Override
  public int hashCode() {
    return Objects.hash(database, spanner, zookeeper, dynamoDb);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GlobalRefDbConfig other = (GlobalRefDbConfig) obj;
    return database == other.database
        && Objects.equals(spanner, other.spanner)
        && Objects.equals(zookeeper, other.zookeeper)
        && Objects.equals(dynamoDb, other.dynamoDb);
  }

  @Override
  public String toString() {
    return "GlobalRefDbConfig [database="
        + database
        + ", zookeeper="
        + zookeeper
        + ", spanner="
        + spanner
        + ", dynamodb="
        + dynamoDb
        + "]";
  }

  public enum RefDatabase {
    NONE,
    ZOOKEEPER,
    SPANNER,
    DYNAMODB
  }
}
