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

package com.google.gerrit.k8s.operator.receiver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.shared.model.IngressConfig;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.sundr.builder.annotations.Buildable;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
@Buildable(
    editableEnabled = false,
    validationEnabled = false,
    generateBuilderPackage = true,
    lazyCollectionInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
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
    receiverSpec.setStorage(gerritCluster.getSpec().getStorage());
    IngressConfig ingressConfig = new IngressConfig();
    ingressConfig.setHost(gerritCluster.getSpec().getIngress().getHost());
    ingressConfig.setType(gerritCluster.getSpec().getIngress().getType());
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
