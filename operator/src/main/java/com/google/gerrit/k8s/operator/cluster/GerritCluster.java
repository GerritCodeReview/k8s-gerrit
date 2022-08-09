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

package com.google.gerrit.k8s.operator.cluster;

import static com.google.gerrit.k8s.operator.cluster.GerritLogsPVC.LOGS_PVC_NAME;
import static com.google.gerrit.k8s.operator.cluster.GitRepositoriesPVC.REPOSITORY_PVC_NAME;
import static com.google.gerrit.k8s.operator.cluster.NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Group("gerritoperator.google.com")
@Version("v1alpha1")
@ShortNames("gclus")
public class GerritCluster extends CustomResource<GerritClusterSpec, Status> implements Namespaced {
  private static final long serialVersionUID = 1L;
  private static final String GIT_REPOSITORIES_VOLUME_NAME = "git-repositories";
  private static final String LOGS_VOLUME_NAME = "logs";
  private static final String NFS_IMAPD_CONFIG_VOLUME_NAME = "nfs-config";

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

  @JsonIgnore
  public Map<String, String> getLabels(String component, String createdBy) {
    Map<String, String> labels = new HashMap<>();

    labels.put("app.kubernetes.io/name", "gerrit");
    labels.put("app.kubernetes.io/instance", getMetadata().getName());
    labels.put("app.kubernetes.io/version", getClass().getPackage().getImplementationVersion());
    labels.put("app.kubernetes.io/component", component);
    labels.put("app.kubernetes.io/part-of", getMetadata().getName());
    labels.put("app.kubernetes.io/managed-by", "gerrit-operator");
    labels.put("app.kubernetes.io/created-by", createdBy);

    return labels;
  }

  @JsonIgnore
  public Volume getGitRepositoriesVolume() {
    return new VolumeBuilder()
        .withName(GIT_REPOSITORIES_VOLUME_NAME)
        .withNewPersistentVolumeClaim()
        .withClaimName(REPOSITORY_PVC_NAME)
        .endPersistentVolumeClaim()
        .build();
  }

  @JsonIgnore
  public VolumeMount getGitRepositoriesVolumeMount() {
    return new VolumeMountBuilder()
        .withName(GIT_REPOSITORIES_VOLUME_NAME)
        .withMountPath("/var/gerrit/git")
        .build();
  }

  @JsonIgnore
  public Volume getLogsVolume() {
    return new VolumeBuilder()
        .withName(LOGS_VOLUME_NAME)
        .withNewPersistentVolumeClaim()
        .withClaimName(LOGS_PVC_NAME)
        .endPersistentVolumeClaim()
        .build();
  }

  @JsonIgnore
  public VolumeMount getLogsVolumeMount() {
    return new VolumeMountBuilder()
        .withName(LOGS_VOLUME_NAME)
        .withMountPath("/var/gerrit/logs")
        .build();
  }

  @JsonIgnore
  public Volume getNfsImapdConfigVolume() {
    return new VolumeBuilder()
        .withName(NFS_IMAPD_CONFIG_VOLUME_NAME)
        .withNewConfigMap()
        .withName(NFS_IDMAPD_CM_NAME)
        .endConfigMap()
        .build();
  }

  @JsonIgnore
  public VolumeMount getNfsImapdConfigVolumeMount() {
    return new VolumeMountBuilder()
        .withName(NFS_IMAPD_CONFIG_VOLUME_NAME)
        .withMountPath("/etc/idmapd.conf")
        .withSubPath("idmapd.conf")
        .build();
  }
}
