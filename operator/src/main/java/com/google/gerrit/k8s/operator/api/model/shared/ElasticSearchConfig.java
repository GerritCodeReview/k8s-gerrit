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

public class ElasticSearchConfig {
  private String server;
  private String username = "elastic";
  private String prefix;
  private int numberOfShards = 1;
  private int numberOfReplicas = 1;
  private int maxResultWindow = Integer.MAX_VALUE;
  private int connectTimeout = 1;
  private int socketTimeout = 30;
  private String codec = "default";

  public String getServer() {
    return server;
  }

  public void setServer(String server) {
    this.server = server;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  public int getMaxResultWindow() {
    return maxResultWindow;
  }

  public void setMaxResultWindow(int maxResultWindow) {
    this.maxResultWindow = maxResultWindow;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(int socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public String getCodec() {
    return codec;
  }

  public void setCodec(String codec) {
    this.codec = codec;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        codec,
        connectTimeout,
        maxResultWindow,
        numberOfReplicas,
        numberOfShards,
        prefix,
        server,
        socketTimeout,
        username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ElasticSearchConfig other = (ElasticSearchConfig) obj;
    return Objects.equals(codec, other.codec)
        && connectTimeout == other.connectTimeout
        && maxResultWindow == other.maxResultWindow
        && numberOfReplicas == other.numberOfReplicas
        && numberOfShards == other.numberOfShards
        && Objects.equals(prefix, other.prefix)
        && Objects.equals(server, other.server)
        && socketTimeout == other.socketTimeout
        && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return "ElasticSearchConfig [server="
        + server
        + ", username="
        + username
        + ", prefix="
        + prefix
        + ", numberOfShards="
        + numberOfShards
        + ", numberOfReplicas="
        + numberOfReplicas
        + ", maxResultWindow="
        + maxResultWindow
        + ", connectTimeout="
        + connectTimeout
        + ", socketTimeout="
        + socketTimeout
        + ", codec="
        + codec
        + "]";
  }
}
