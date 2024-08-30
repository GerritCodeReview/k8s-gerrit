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

import static com.google.gerrit.k8s.operator.cluster.dependent.NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME;
import static com.google.gerrit.k8s.operator.cluster.dependent.SharedPVC.SHARED_PVC_NAME;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.SharedStorage.ExternalPVCConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
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
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Group("gerritoperator.google.com")
@Version(Constants.VERSION)
@ShortNames("gclus")
public class GerritCluster extends CustomResource<GerritClusterSpec, GerritClusterStatus>
    implements Namespaced {
  private static final long serialVersionUID = 2L;
  private static final String SHARED_VOLUME_NAME = "shared";
  private static final String NFS_IDMAPD_CONFIG_VOLUME_NAME = "nfs-config";
  private static final int GERRIT_FS_UID = 1000;
  private static final int GERRIT_FS_GID = 100;
  public static final String PLUGIN_CACHE_MOUNT_PATH = "/var/mnt/plugin_cache";
  public static final String PLUGIN_CACHE_SUB_DIR = "plugin_cache";

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

  @JsonIgnore
  public static Volume getSharedVolume(ExternalPVCConfig externalPVC) {
    String claimName = externalPVC.isEnabled() ? externalPVC.getClaimName() : SHARED_PVC_NAME;
    return new VolumeBuilder()
        .withName(SHARED_VOLUME_NAME)
        .withNewPersistentVolumeClaim()
        .withClaimName(claimName)
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
        .withName(SHARED_VOLUME_NAME)
        .withSubPath("git")
        .withMountPath(mountPath)
        .build();
  }

  @JsonIgnore
  public static VolumeMount getHAShareVolumeMount() {
    return getSharedVolumeMount("shared", "/var/mnt/shared");
  }

  @JsonIgnore
  public static VolumeMount getPluginCacheVolumeMount() {
    return getSharedVolumeMount(PLUGIN_CACHE_SUB_DIR, "/var/mnt/plugin_cache");
  }

  @JsonIgnore
  public static VolumeMount getSharedVolumeMount(String subPath, String mountPath) {
    return new VolumeMountBuilder()
        .withName(SHARED_VOLUME_NAME)
        .withSubPath(subPath)
        .withMountPath(mountPath)
        .build();
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
  public Container createNfsInitContainer() {
    return createNfsInitContainer(
        getSpec().getStorage().getStorageClasses().getNfsWorkaround().getIdmapdConfig() != null,
        getSpec().getContainerImages());
  }

  @JsonIgnore
  public static Container createNfsInitContainer(
      boolean configureIdmapd, ContainerImageConfig imageConfig) {
    return createNfsInitContainer(configureIdmapd, imageConfig, List.of());
  }

  @JsonIgnore
  public static Container createNfsInitContainer(
      boolean configureIdmapd,
      ContainerImageConfig imageConfig,
      List<VolumeMount> additionalVolumeMounts) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(getGitRepositoriesVolumeMount());

    volumeMounts.addAll(additionalVolumeMounts);

    StringBuilder args = new StringBuilder();
    args.append("chown -R ");
    args.append(GERRIT_FS_UID);
    args.append(":");
    args.append(GERRIT_FS_GID);
    args.append(" ");
    for (VolumeMount vm : volumeMounts) {
      args.append(vm.getMountPath());
      args.append(" ");
    }

    if (configureIdmapd) {
      volumeMounts.add(getNfsImapdConfigVolumeMount());
    }

    return new ContainerBuilder()
        .withName("nfs-init")
        .withImagePullPolicy(imageConfig.getImagePullPolicy())
        .withImage(imageConfig.getBusyBox().getBusyBoxImage())
        .withCommand(List.of("sh", "-c"))
        .withArgs(args.toString().trim())
        .withVolumeMounts(volumeMounts)
        .build();
  }
}
