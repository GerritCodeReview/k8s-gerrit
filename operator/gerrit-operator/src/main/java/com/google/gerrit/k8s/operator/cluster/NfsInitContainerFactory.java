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

package com.google.gerrit.k8s.operator.cluster;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.ArrayList;
import java.util.List;

public class NfsInitContainerFactory {
  private static final int GERRIT_FS_UID = 1000;
  private static final int GERRIT_FS_GID = 100;

  public static Container create(GerritCluster gerritCluster) {
    return create(
        gerritCluster.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled(),
        gerritCluster.getSpec().getContainerImages(),
        List.of());
  }

  public static Container create(boolean configureIdmapd, ContainerImageConfig imageConfig) {
    return create(configureIdmapd, imageConfig, List.of());
  }

  public static Container create(
      boolean configureIdmapd,
      ContainerImageConfig imageConfig,
      List<VolumeMount> additionalVolumeMounts) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(GerritClusterSharedVolumeMountFactory.createForGitRepos());

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
      volumeMounts.add(NfsIdmapdVolumeMountFactory.create());
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
