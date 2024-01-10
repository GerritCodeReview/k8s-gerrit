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

package com.google.gerrit.k8s.operator.v1beta3.api.model.receiver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.v1beta3.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.IngressConfig;
import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.StorageConfig;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
@Deprecated
public class ReceiverTemplate implements KubernetesResource {
  private static final long serialVersionUID = 1L;

  @JsonProperty("metadata")
  private ObjectMeta metadata;

  @JsonProperty("spec")
  private ReceiverTemplateSpec spec;

  public ReceiverTemplate() {}

  @JsonProperty("metadata")
  public ObjectMeta getMetadata() {
    return metadata;
  }

  @JsonProperty("metadata")
  public void setMetadata(ObjectMeta metadata) {
    this.metadata = metadata;
  }

  @JsonProperty("spec")
  public ReceiverTemplateSpec getSpec() {
    return spec;
  }

  @JsonProperty("spec")
  public void setSpec(ReceiverTemplateSpec spec) {
    this.spec = spec;
  }

  @JsonIgnore
  public Receiver toReceiver(GerritCluster gerritCluster) {
    Receiver receiver = new Receiver();
    receiver.setMetadata(getReceiverMetadata(gerritCluster));
    ReceiverSpec receiverSpec = new ReceiverSpec(spec);
    receiverSpec.setContainerImages(gerritCluster.getSpec().getContainerImages());
    receiverSpec.setStorage(new StorageConfig(gerritCluster.getSpec().getStorage()));
    IngressConfig ingressConfig = new IngressConfig();
    ingressConfig.setHost(gerritCluster.getSpec().getIngress().getHost());
    ingressConfig.setTlsEnabled(gerritCluster.getSpec().getIngress().getTls().isEnabled());
    receiverSpec.setIngress(ingressConfig);
    receiver.setSpec(receiverSpec);
    return receiver;
  }

  @JsonIgnore
  private ObjectMeta getReceiverMetadata(GerritCluster gerritCluster) {
    return new ObjectMetaBuilder()
        .withName(metadata.getName())
        .withLabels(metadata.getLabels())
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .build();
  }
}
