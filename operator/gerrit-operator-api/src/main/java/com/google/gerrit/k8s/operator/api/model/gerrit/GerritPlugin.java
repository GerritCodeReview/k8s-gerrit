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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

public class GerritPlugin extends GerritModule {
  private static final long serialVersionUID = 1L;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private boolean installAsLibrary = false;

  public boolean isInstallAsLibrary() {
    return installAsLibrary;
  }

  public void setInstallAsLibrary(boolean installAsLibrary) {
    this.installAsLibrary = installAsLibrary;
  }

  @JsonIgnore
  public boolean isPackagedPlugin() {
    return getUrl() == null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(installAsLibrary);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    GerritPlugin other = (GerritPlugin) obj;
    return installAsLibrary == other.installAsLibrary;
  }

  @Override
  public String toString() {
    return "GerritPlugin [installAsLibrary=" + installAsLibrary + "]";
  }
}
