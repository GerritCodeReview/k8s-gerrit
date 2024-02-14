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

package com.google.gerrit.k8s.operator.api.model.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig;
import com.google.gerrit.k8s.operator.api.model.shared.IngressConfig;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "spec"})
public class GerritTemplate implements KubernetesResource {
  private static final long serialVersionUID = 1L;

  @JsonProperty("metadata")
  private ObjectMeta metadata;

  @JsonProperty("spec")
  private GerritTemplateSpec spec;

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
  public GerritTemplateSpec getSpec() {
    return spec;
  }

  @JsonProperty("spec")
  public void setSpec(GerritTemplateSpec spec) {
    this.spec = spec;
  }

  @JsonIgnore
  public Gerrit toGerrit(GerritCluster gerritCluster) {
    Gerrit gerrit = new Gerrit();
    gerrit.setMetadata(getGerritMetadata(gerritCluster));
    GerritSpec gerritSpec = new GerritSpec(spec);
    gerritSpec.setContainerImages(gerritCluster.getSpec().getContainerImages());
    gerritSpec.setStorage(gerritCluster.getSpec().getStorage());
    IngressConfig ingressConfig = new IngressConfig();
    ingressConfig.setEnabled(gerritCluster.getSpec().getIngress().isEnabled());
    ingressConfig.setHost(gerritCluster.getSpec().getIngress().getHost());
    ingressConfig.setTlsEnabled(gerritCluster.getSpec().getIngress().getTls().isEnabled());
    ingressConfig.setSsh(gerritCluster.getSpec().getIngress().getSsh());
    gerritSpec.setIngress(ingressConfig);
    gerritSpec.setServerId(getServerId(gerritCluster));
    gerritSpec.setFluentBitSidecar(gerritCluster.getSpec().getFluentBitSidecar());
    if (getSpec().isHighlyAvailablePrimary()) {
      GlobalRefDbConfig refdb = gerritCluster.getSpec().getRefdb();
      if (refdb.getZookeeper() != null && refdb.getZookeeper().getRootNode() == null) {
        refdb
            .getZookeeper()
            .setRootNode(
                gerritCluster.getMetadata().getNamespace()
                    + "/"
                    + gerritCluster.getMetadata().getName());
      }
      gerritSpec.setRefdb(gerritCluster.getSpec().getRefdb());
    }

    setSshdAdvertisedReadPort(gerritCluster, gerritSpec);

    gerrit.setSpec(gerritSpec);
    return gerrit;
  }

  @JsonIgnore
  public void setSshdAdvertisedReadPort(GerritCluster gerritCluster, GerritSpec gerritSpec) {
    if (spec.getMode() == GerritMode.PRIMARY) {
      List<GerritTemplate> gerrits = gerritCluster.getSpec().getGerrits();
      int replicaSshdPort = 0;
      int primarySshdPort = 0;
      for (GerritTemplate gt : gerrits) {
        switch (gt.getSpec().getMode()) {
          case REPLICA:
            replicaSshdPort = gt.getSpec().getService().getSshPort();
            break;
          case PRIMARY:
            primarySshdPort = gt.getSpec().getService().getSshPort();
            break;
          default:
        }
      }
      if (replicaSshdPort > 0 && primarySshdPort > 0) {
        gerritSpec.setSshdAdvertisedReadPort(replicaSshdPort);
      }
    }
  }

  @JsonIgnore
  private ObjectMeta getGerritMetadata(GerritCluster gerritCluster) {
    return new ObjectMetaBuilder()
        .withName(metadata.getName())
        .withLabels(metadata.getLabels())
        .withNamespace(gerritCluster.getMetadata().getNamespace())
        .build();
  }

  private String getServerId(GerritCluster gerritCluster) {
    String serverId = gerritCluster.getSpec().getServerId();
    return serverId.isBlank()
        ? gerritCluster.getMetadata().getNamespace() + "/" + gerritCluster.getMetadata().getName()
        : serverId;
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
    GerritTemplate other = (GerritTemplate) obj;
    return Objects.equals(metadata, other.metadata) && Objects.equals(spec, other.spec);
  }

  @Override
  public String toString() {
    return "GerritTemplate [metadata=" + metadata + ", spec=" + spec + "]";
  }
}
