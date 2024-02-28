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

package com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.Objects;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
public class IncomingReplicationTaskTemplate implements KubernetesResource {

  private static final long serialVersionUID = 1L;

  @JsonProperty("metadata")
  private ObjectMeta metadata;

  @JsonProperty("spec")
  private IncomingReplicationTaskTemplateSpec spec;

  public ObjectMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public IncomingReplicationTaskTemplateSpec getSpec() {
    return spec;
  }

  public void setSpec(IncomingReplicationTaskTemplateSpec spec) {
    this.spec = spec;
  }

  @JsonIgnore
  public IncomingReplicationTask toIncomingReplicationTask(GerritCluster gerritCluster) {
    IncomingReplicationTaskSpec incomingReplicationTaskSpec = new IncomingReplicationTaskSpec(spec);
    incomingReplicationTaskSpec.setContainerImages(gerritCluster.getSpec().getContainerImages());
    incomingReplicationTaskSpec.setStorage(gerritCluster.getSpec().getStorage());
    IncomingReplicationTask incomingReplTask = new IncomingReplicationTask();
    incomingReplTask.setSpec(incomingReplicationTaskSpec);
    incomingReplTask.setMetadata(getIncomingReplicationTaskMetadata(gerritCluster));
    return incomingReplTask;
  }

  @JsonIgnore
  private ObjectMeta getIncomingReplicationTaskMetadata(GerritCluster gerritCluster) {
    return new ObjectMetaBuilder()
        .withName(getMetadata().getName())
        .withLabels(getMetadata().getLabels())
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .build();
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadata, spec);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncomingReplicationTaskTemplate other = (IncomingReplicationTaskTemplate) obj;
    return Objects.equals(metadata, other.metadata) && Objects.equals(spec, other.spec);
  }

  @Override
  public String toString() {
    return "IncomingReplicationTaskTemplate [metadata=" + metadata + ", spec=" + spec + "]";
  }
}
