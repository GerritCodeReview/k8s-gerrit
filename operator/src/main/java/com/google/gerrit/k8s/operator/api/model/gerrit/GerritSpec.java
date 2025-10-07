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

import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.FluentBitSidecarConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IndexConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IngressConfig;
import java.util.Objects;

public class GerritSpec extends GerritTemplateSpec {
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritStorageConfig storage = new GerritStorageConfig();
  private IngressConfig ingress = new IngressConfig();
  private GlobalRefDbConfig refdb = new GlobalRefDbConfig();
  private IndexConfig index = new IndexConfig();
  private String serverId = "";
  private FluentBitSidecarConfig fluentBitSidecar = new FluentBitSidecarConfig();
  private int sshdAdvertisedReadPort = 0;

  public GerritSpec() {}

  public GerritSpec(GerritTemplateSpec templateSpec) {
    super(templateSpec);
  }

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public GerritStorageConfig getStorage() {
    return storage;
  }

  public void setStorage(GerritStorageConfig storage) {
    this.storage = storage;
  }

  public IngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(IngressConfig ingress) {
    this.ingress = ingress;
  }

  public GlobalRefDbConfig getRefdb() {
    return refdb;
  }

  public void setRefdb(GlobalRefDbConfig refdb) {
    this.refdb = refdb;
  }

  public IndexConfig getIndex() {
    return index;
  }

  public void setIndex(IndexConfig index) {
    this.index = index;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public FluentBitSidecarConfig getFluentBitSidecar() {
    return fluentBitSidecar;
  }

  public void setFluentBitSidecar(FluentBitSidecarConfig fluentBitSidecar) {
    this.fluentBitSidecar = fluentBitSidecar;
  }

  public int getSshdAdvertisedReadPort() {
    return sshdAdvertisedReadPort;
  }

  public void setSshdAdvertisedReadPort(int sshdAdvertisedReadPort) {
    this.sshdAdvertisedReadPort = sshdAdvertisedReadPort;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result =
        prime * result
            + Objects.hash(
                containerImages,
                fluentBitSidecar,
                index,
                ingress,
                refdb,
                serverId,
                sshdAdvertisedReadPort,
                storage);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    GerritSpec other = (GerritSpec) obj;
    return Objects.equals(containerImages, other.containerImages)
        && Objects.equals(fluentBitSidecar, other.fluentBitSidecar)
        && Objects.equals(index, other.index)
        && Objects.equals(ingress, other.ingress)
        && Objects.equals(refdb, other.refdb)
        && Objects.equals(serverId, other.serverId)
        && sshdAdvertisedReadPort == other.sshdAdvertisedReadPort
        && Objects.equals(storage, other.storage);
  }

  @Override
  public String toString() {
    return "GerritSpec [containerImages="
        + containerImages
        + ", storage="
        + storage
        + ", ingress="
        + ingress
        + ", refdb="
        + refdb
        + ", index="
        + index
        + ", serverId="
        + serverId
        + ", fluentBitSidecar="
        + fluentBitSidecar
        + ", sshdAdvertisedReadPort="
        + sshdAdvertisedReadPort
        + "]";
  }
}
