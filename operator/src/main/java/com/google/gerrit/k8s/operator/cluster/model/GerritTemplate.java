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

package com.google.gerrit.k8s.operator.cluster.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.gerrit.model.GerritSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.sundr.builder.annotations.Buildable;
import org.apache.commons.lang3.NotImplementedException;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
@Buildable(
    editableEnabled = false,
    validationEnabled = false,
    generateBuilderPackage = true,
    lazyCollectionInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class GerritTemplate implements KubernetesResource, HasMetadata {
  private static final long serialVersionUID = 1L;

  @JsonProperty("metadata")
  private ObjectMeta metadata;

  @JsonProperty("spec")
  private GerritSpec spec;

  public GerritTemplate() {}

  @JsonProperty("metadata")
  public ObjectMeta getMetadata() {
    return metadata;
  }

  @JsonProperty("metadata")
  public void setMetadata(ObjectMeta metadata) {
    this.metadata = metadata;
  }

  @JsonProperty("spec")
  public GerritSpec getSpec() {
    return spec;
  }

  @JsonProperty("spec")
  public void setSpec(GerritSpec spec) {
    this.spec = spec;
  }

  @Override
  public void setApiVersion(String version) {
    // Since this is not a real resource, this is not required.
    throw new NotImplementedException();
  }
}
