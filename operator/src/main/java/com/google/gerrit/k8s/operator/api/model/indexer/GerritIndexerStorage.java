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

package com.google.gerrit.k8s.operator.api.model.indexer;

import java.util.Objects;

public class GerritIndexerStorage {
  private GerritIndexerInputVolumeRef site = GerritIndexerInputVolumeRef.defaultForSiteVolume();
  private GerritIndexerInputVolumeRef repositories =
      GerritIndexerInputVolumeRef.defaultForRepositoriesVolume();
  private GerritIndexerVolumeRef output = new GerritIndexerVolumeRef();

  public GerritIndexerInputVolumeRef getSite() {
    return site;
  }

  public void setSite(GerritIndexerInputVolumeRef site) {
    this.site = site;
  }

  public GerritIndexerInputVolumeRef getRepositories() {
    return repositories;
  }

  public void setRepositories(GerritIndexerInputVolumeRef repositories) {
    this.repositories = repositories;
  }

  public GerritIndexerVolumeRef getOutput() {
    return output;
  }

  public void setOutput(GerritIndexerVolumeRef output) {
    this.output = output;
  }

  @Override
  public int hashCode() {
    return Objects.hash(output, repositories, site);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritIndexerStorage other = (GerritIndexerStorage) obj;
    return Objects.equals(output, other.output)
        && Objects.equals(repositories, other.repositories)
        && Objects.equals(site, other.site);
  }

  @Override
  public String toString() {
    return "GerritIndexerStorage [site="
        + site
        + ", repositories="
        + repositories
        + ", output="
        + output
        + "]";
  }
}
