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

package com.google.gerrit.k8s.operator.api.model.cluster;

import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.FluentBitSidecarConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritClusterIngressConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IndexConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GerritClusterSpec {

  private GerritStorageConfig storage = new GerritStorageConfig();
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private GerritClusterIngressConfig ingress = new GerritClusterIngressConfig();
  private GlobalRefDbConfig refdb = new GlobalRefDbConfig();
  private IndexConfig index = new IndexConfig();
  private String serverId = "";
  private List<GerritTemplate> gerrits = new ArrayList<>();
  private ReceiverTemplate receiver;
  private FluentBitSidecarConfig fluentBitSidecar = new FluentBitSidecarConfig();
  private ScheduledTasks scheduledTasks = new ScheduledTasks();

  public GerritStorageConfig getStorage() {
    return storage;
  }

  public void setStorage(GerritStorageConfig storage) {
    this.storage = storage;
  }

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public GerritClusterIngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(GerritClusterIngressConfig ingress) {
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

  public List<GerritTemplate> getGerrits() {
    return gerrits;
  }

  public void setGerrits(List<GerritTemplate> gerrits) {
    this.gerrits = gerrits;
  }

  public ReceiverTemplate getReceiver() {
    return receiver;
  }

  public void setReceiver(ReceiverTemplate receiver) {
    this.receiver = receiver;
  }

  public FluentBitSidecarConfig getFluentBitSidecar() {
    return fluentBitSidecar;
  }

  public void setFluentBitSidecar(FluentBitSidecarConfig fluentBitSidecar) {
    this.fluentBitSidecar = fluentBitSidecar;
  }

  public ScheduledTasks getScheduledTasks() {
    return scheduledTasks;
  }

  public void setScheduledTasks(ScheduledTasks scheduledTasks) {
    this.scheduledTasks = scheduledTasks;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        containerImages,
        fluentBitSidecar,
        gerrits,
        index,
        ingress,
        receiver,
        refdb,
        scheduledTasks,
        serverId,
        storage);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GerritClusterSpec other = (GerritClusterSpec) obj;
    return Objects.equals(containerImages, other.containerImages)
        && Objects.equals(fluentBitSidecar, other.fluentBitSidecar)
        && Objects.equals(gerrits, other.gerrits)
        && Objects.equals(index, other.index)
        && Objects.equals(ingress, other.ingress)
        && Objects.equals(receiver, other.receiver)
        && Objects.equals(refdb, other.refdb)
        && Objects.equals(scheduledTasks, other.scheduledTasks)
        && Objects.equals(serverId, other.serverId)
        && Objects.equals(storage, other.storage);
  }

  @Override
  public String toString() {
    return "GerritClusterSpec [storage="
        + storage
        + ", containerImages="
        + containerImages
        + ", ingress="
        + ingress
        + ", refdb="
        + refdb
        + ", index="
        + index
        + ", serverId="
        + serverId
        + ", gerrits="
        + gerrits
        + ", receiver="
        + receiver
        + ", fluentBitSidecar="
        + fluentBitSidecar
        + ", scheduledTasks="
        + scheduledTasks
        + "]";
  }
}
