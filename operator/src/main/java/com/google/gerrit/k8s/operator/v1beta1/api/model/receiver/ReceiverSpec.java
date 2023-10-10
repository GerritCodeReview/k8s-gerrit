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

package com.google.gerrit.k8s.operator.v1beta1.api.model.receiver;

import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.IngressConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.StorageConfig;

@Deprecated
public class ReceiverSpec extends ReceiverTemplateSpec {
  private ContainerImageConfig containerImages = new ContainerImageConfig();
  private StorageConfig storage = new StorageConfig();
  private IngressConfig ingress = new IngressConfig();

  public ReceiverSpec() {}

  public ReceiverSpec(ReceiverTemplateSpec templateSpec) {
    super(templateSpec);
  }

  public ContainerImageConfig getContainerImages() {
    return containerImages;
  }

  public void setContainerImages(ContainerImageConfig containerImages) {
    this.containerImages = containerImages;
  }

  public StorageConfig getStorage() {
    return storage;
  }

  public void setStorage(StorageConfig storage) {
    this.storage = storage;
  }

  public IngressConfig getIngress() {
    return ingress;
  }

  public void setIngress(IngressConfig ingress) {
    this.ingress = ingress;
  }
}
