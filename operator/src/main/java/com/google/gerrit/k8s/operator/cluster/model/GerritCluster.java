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

package com.google.gerrit.k8s.operator.cluster.model;

import static com.google.gerrit.k8s.operator.cluster.dependent.GerritLogsPVC.LOGS_PVC_NAME;
import static com.google.gerrit.k8s.operator.cluster.dependent.GitRepositoriesPVC.REPOSITORY_PVC_NAME;
import static com.google.gerrit.k8s.operator.cluster.dependent.NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberSpec;
import com.google.gerrit.k8s.operator.shared.model.ContainerImageConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Group("gerritoperator.google.com")
@Version("v1alpha3")
@ShortNames("gclus")
public class GerritCluster extends CustomResource<GerritClusterSpec, GerritClusterStatus>
    implements Namespaced {
  private static final long serialVersionUID = 2L;
  private static final String GIT_REPOSITORIES_VOLUME_NAME = "git-repositories";
  private static final String LOGS_VOLUME_NAME = "logs";
  private static final String NFS_IDMAPD_CONFIG_VOLUME_NAME = "nfs-config";
  private static final int GERRIT_FS_UID = 1000;
  private static final int GERRIT_FS_GID = 100;

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

  @JsonIgnore
  public Map<String, String> getLabels(String component, String createdBy) {
    Map<String, String> labels = new HashMap<>();

    labels.putAll(getSelectorLabels(component));
    labels.put("app.kubernetes.io/version", getClass().getPackage().getImplementationVersion());
    labels.put("app.kubernetes.io/created-by", createdBy);

    return labels;
  }

  // TODO(Thomas): Having so many string parameters is bad. The only parameter should be the
  // Kubernetes resource that implements an interface that provides methods to retrieve the
  // required information.
  @JsonIgnore
  public static Map<String, String> getLabels(String instance, String component, String createdBy) {
    Map<String, String> labels = new HashMap<>();

    labels.putAll(getSelectorLabels(instance, component));
    labels.put(
        "app.kubernetes.io/version", GerritCluster.class.getPackage().getImplementationVersion());
    labels.put("app.kubernetes.io/created-by", createdBy);

    return labels;
  }

  @JsonIgnore
  public Map<String, String> getSelectorLabels(String component) {
    return getSelectorLabels(getMetadata().getName(), component);
  }

  @JsonIgnore
  public static Map<String, String> getSelectorLabels(String instance, String component) {
    Map<String, String> labels = new HashMap<>();

    labels.put("app.kubernetes.io/name", "gerrit");
    labels.put("app.kubernetes.io/instance", instance);
    labels.put("app.kubernetes.io/component", component);
    labels.put("app.kubernetes.io/part-of", instance);
    labels.put("app.kubernetes.io/managed-by", "gerrit-operator");

    return labels;
  }

  @JsonIgnore
  public static Volume getGitRepositoriesVolume() {
    return new VolumeBuilder()
        .withName(GIT_REPOSITORIES_VOLUME_NAME)
        .withNewPersistentVolumeClaim()
        .withClaimName(REPOSITORY_PVC_NAME)
        .endPersistentVolumeClaim()
        .build();
  }

  @JsonIgnore
  public static VolumeMount getGitRepositoriesVolumeMount() {
    return getGitRepositoriesVolumeMount("/var/mnt/git");
  }

  @JsonIgnore
  public static VolumeMount getGitRepositoriesVolumeMount(String mountPath) {
    return new VolumeMountBuilder()
        .withName(GIT_REPOSITORIES_VOLUME_NAME)
        .withMountPath(mountPath)
        .build();
  }

  @JsonIgnore
  public static Volume getLogsVolume() {
    return new VolumeBuilder()
        .withName(LOGS_VOLUME_NAME)
        .withNewPersistentVolumeClaim()
        .withClaimName(LOGS_PVC_NAME)
        .endPersistentVolumeClaim()
        .build();
  }

  @JsonIgnore
  public static VolumeMount getLogsVolumeMount() {
    return getLogsVolumeMount("/var/mnt/logs");
  }

  @JsonIgnore
  public static VolumeMount getLogsVolumeMount(String mountPath) {
    return new VolumeMountBuilder().withName(LOGS_VOLUME_NAME).withMountPath(mountPath).build();
  }

  @JsonIgnore
  public static Volume getNfsImapdConfigVolume() {
    return new VolumeBuilder()
        .withName(NFS_IDMAPD_CONFIG_VOLUME_NAME)
        .withNewConfigMap()
        .withName(NFS_IDMAPD_CM_NAME)
        .endConfigMap()
        .build();
  }

  @JsonIgnore
  public static VolumeMount getNfsImapdConfigVolumeMount() {
    return new VolumeMountBuilder()
        .withName(NFS_IDMAPD_CONFIG_VOLUME_NAME)
        .withMountPath("/etc/idmapd.conf")
        .withSubPath("idmapd.conf")
        .build();
  }

  @JsonIgnore
  public static boolean isMemberPartOfCluster(
      GerritClusterMemberSpec memberSpec, GerritCluster cluster) {
    return memberSpec.getCluster().equals(cluster.getMetadata().getName());
  }

  @JsonIgnore
  public Container createNfsInitContainer() {
    return createNfsInitContainer(
        getSpec().getStorage().getStorageClasses().getNfsWorkaround().getIdmapdConfig() != null,
        getSpec().getContainerImages());
  }

  @JsonIgnore
  public static Container createNfsInitContainer(
      boolean configureIdmapd, ContainerImageConfig imageConfig) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(getLogsVolumeMount());
    volumeMounts.add(getGitRepositoriesVolumeMount());

    if (configureIdmapd) {
      volumeMounts.add(getNfsImapdConfigVolumeMount());
    }

    return new ContainerBuilder()
        .withName("nfs-init")
        .withImagePullPolicy(imageConfig.getImagePullPolicy())
        .withImage(imageConfig.getBusyBox().getBusyBoxImage())
        .withCommand(List.of("sh", "-c"))
        .withArgs(
            String.format(
                "chown -R %d:%d /var/mnt/logs /var/mnt/git", GERRIT_FS_UID, GERRIT_FS_GID))
        .withEnv(getPodNameEnvVar())
        .withVolumeMounts(volumeMounts)
        .build();
  }

  @JsonIgnore
  public static EnvVar getPodNameEnvVar() {
    return new EnvVarBuilder()
        .withName("POD_NAME")
        .withNewValueFrom()
        .withNewFieldRef()
        .withFieldPath("metadata.name")
        .endFieldRef()
        .endValueFrom()
        .build();
  }
}
