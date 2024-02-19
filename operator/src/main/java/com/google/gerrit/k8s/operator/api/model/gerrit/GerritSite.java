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

package com.google.gerrit.k8s.operator.api.model.gerrit;

import io.fabric8.kubernetes.api.model.Quantity;
import java.io.Serializable;
import java.util.Objects;

public class GerritSite implements Serializable {
  private static final long serialVersionUID = 1L;
  Quantity size;

  public Quantity getSize() {
    return size;
  }

  public void setSize(Quantity size) {
    this.size = size;
  }

  @Override
  public int hashCode() {
    return Objects.hash(size);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritSite other = (GerritSite) obj;
    return Objects.equals(size, other.size);
  }

  @Override
  public String toString() {
    return "GerritSite [size=" + size + "]";
  }
}
