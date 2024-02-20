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

public class ZookeeperRefDbConfig {
  private String connectString;
  private String rootNode;

  public String getConnectString() {
    return connectString;
  }

  public void setConnectString(String connectString) {
    this.connectString = connectString;
  }

  public String getRootNode() {
    return rootNode;
  }

  public void setRootNode(String rootNode) {
    this.rootNode = rootNode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectString, rootNode);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZookeeperRefDbConfig other = (ZookeeperRefDbConfig) obj;
    return Objects.equals(connectString, other.connectString)
        && Objects.equals(rootNode, other.rootNode);
  }

  @Override
  public String toString() {
    return "ZookeeperRefDbConfig [connectString=" + connectString + ", rootNode=" + rootNode + "]";
  }
}
