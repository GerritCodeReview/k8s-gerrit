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

package com.google.gerrit.k8s.operator.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.sundr.builder.annotations.Buildable;
import java.util.Map;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
@Buildable(
    editableEnabled = false,
    validationEnabled = false,
    generateBuilderPackage = true,
    lazyCollectionInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class GerritTemplate implements KubernetesResource {
  private static final long serialVersionUID = 1L;

  public static final String GERRIT_CLUSTER_ANNOTATION = "gerritoperator.google.com/cluster";

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

  @JsonIgnore
  public Gerrit toClusterOwnedGerrit(GerritCluster gerritCluster) {
    Gerrit gerrit = new Gerrit();
    gerrit.setMetadata(getGerritMetadata(gerritCluster));
    gerrit.setSpec(spec);
    return gerrit;
  }

  @JsonIgnore
  private ObjectMeta getGerritMetadata(GerritCluster gerritCluster) {
    return new ObjectMetaBuilder()
        .withName(metadata.getName())
        .withLabels(metadata.getLabels())
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .withAnnotations(Map.of(GERRIT_CLUSTER_ANNOTATION, gerritCluster.toString()))
        .withOwnerReferences(
            new OwnerReferenceBuilder()
                .withApiVersion(gerritCluster.getApiVersion())
                .withKind(gerritCluster.getKind())
                .withName(gerritCluster.getMetadata().getName())
                .withUid(gerritCluster.getMetadata().getUid())
                .withController(false)
                .build())
        .build();
  }
}
