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

public class IndexConfig {
  private IndexType type = IndexType.LUCENE;
  private RemoteIndexConfig remoteIndexConfig = new RemoteIndexConfig();

  public IndexType getType() {
    return type;
  }

  public void setType(IndexType type) {
    this.type = type;
  }

  public RemoteIndexConfig getRemoteIndexConfig() {
    return remoteIndexConfig;
  }

  public void setRemoteIndexConfig(RemoteIndexConfig remoteIndexConfig) {
    this.remoteIndexConfig = remoteIndexConfig;
  }

  @Override
  public int hashCode() {
    return Objects.hash(remoteIndexConfig, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IndexConfig other = (IndexConfig) obj;
    return Objects.equals(remoteIndexConfig, other.remoteIndexConfig) && type == other.type;
  }

  @Override
  public String toString() {
    return "IndexConfig [type=" + type + ", remoteIndexConfig=" + remoteIndexConfig + "]";
  }
}
