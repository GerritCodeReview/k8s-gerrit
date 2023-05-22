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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import com.google.gerrit.k8s.operator.cluster.dependent.PluginCachePVC;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.shared.model.NfsWorkaroundConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@KubernetesDependent
public class GerritStatefulSet extends CRUDKubernetesDependentResource<StatefulSet, Gerrit> {

  private static final String SITE_VOLUME_NAME = "gerrit-site";
  public static final int HTTP_PORT = 8080;
  public static final int SSH_PORT = 29418;

  public GerritStatefulSet() {
    super(StatefulSet.class);
  }

  @Override
  protected StatefulSet desired(Gerrit gerrit, Context<Gerrit> context) {
    StatefulSetBuilder stsBuilder = new StatefulSetBuilder();

    List<Container> initContainers = new ArrayList<>();

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      initContainers.add(
          GerritCluster.createNfsInitContainer(
              gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround().getIdmapdConfig()
                  != null,
              gerrit.getSpec().getContainerImages()));
    }

    stsBuilder
        .withApiVersion("apps/v1")
        .withNewMetadata()
        .withName(gerrit.getMetadata().getName())
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withServiceName(GerritService.getName(gerrit))
        .withReplicas(gerrit.getSpec().getReplicas())
        .withNewUpdateStrategy()
        .withNewRollingUpdate()
        .withPartition(gerrit.getSpec().getUpdatePartition())
        .endRollingUpdate()
        .endUpdateStrategy()
        .withNewSelector()
        .withMatchLabels(getSelectorLabels(gerrit))
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withTolerations(gerrit.getSpec().getTolerations())
        .withTopologySpreadConstraints(gerrit.getSpec().getTopologySpreadConstraints())
        .withAffinity(gerrit.getSpec().getAffinity())
        .withPriorityClassName(gerrit.getSpec().getPriorityClassName())
        .withTerminationGracePeriodSeconds(gerrit.getSpec().getGracefulStopTimeout())
        .addAllToImagePullSecrets(gerrit.getSpec().getContainerImages().getImagePullSecrets())
        .withNewSecurityContext()
        .withFsGroup(100L)
        .endSecurityContext()
        .addNewInitContainer()
        .withName("gerrit-init")
        .withImagePullPolicy(gerrit.getSpec().getContainerImages().getImagePullPolicy())
        .withImage(
            gerrit.getSpec().getContainerImages().getGerritImages().getFullImageName("gerrit-init"))
        .withResources(gerrit.getSpec().getResources())
        .addAllToVolumeMounts(getVolumeMounts(gerrit, true))
        .endInitContainer()
        .addAllToInitContainers(initContainers)
        .addNewContainer()
        .withName("gerrit")
        .withImagePullPolicy(gerrit.getSpec().getContainerImages().getImagePullPolicy())
        .withImage(
            gerrit.getSpec().getContainerImages().getGerritImages().getFullImageName("gerrit"))
        .withNewLifecycle()
        .withNewPreStop()
        .withNewExec()
        .withCommand(
            "/bin/ash/", "-c", "kill -2 $(pidof java) && tail --pid=$(pidof java) -f /dev/null")
        .endExec()
        .endPreStop()
        .endLifecycle()
        .withEnv(GerritCluster.getPodNameEnvVar())
        .withPorts(getContainerPorts(gerrit))
        .withResources(gerrit.getSpec().getResources())
        .withStartupProbe(gerrit.getSpec().getStartupProbe())
        .withReadinessProbe(gerrit.getSpec().getReadinessProbe())
        .withLivenessProbe(gerrit.getSpec().getLivenessProbe())
        .addAllToVolumeMounts(getVolumeMounts(gerrit, false))
        .endContainer()
        .addAllToVolumes(getVolumes(gerrit))
        .endSpec()
        .endTemplate()
        .addNewVolumeClaimTemplate()
        .withNewMetadata()
        .withName(SITE_VOLUME_NAME)
        .withLabels(getSelectorLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withAccessModes("ReadWriteOnce")
        .withNewResources()
        .withRequests(Map.of("storage", gerrit.getSpec().getSite().getSize()))
        .endResources()
        .withStorageClassName(gerrit.getSpec().getStorage().getStorageClasses().getReadWriteOnce())
        .endSpec()
        .endVolumeClaimTemplate()
        .endSpec();

    return stsBuilder.build();
  }

  private static String getComponentName(Gerrit gerrit) {
    return String.format("gerrit-statefulset-%s", gerrit.getMetadata().getName());
  }

  public static Map<String, String> getSelectorLabels(Gerrit gerrit) {
    return GerritCluster.getSelectorLabels(
        gerrit.getMetadata().getName(), getComponentName(gerrit));
  }

  private static Map<String, String> getLabels(Gerrit gerrit) {
    return GerritCluster.getLabels(
        gerrit.getMetadata().getName(),
        getComponentName(gerrit),
        GerritReconciler.class.getSimpleName());
  }

  private Set<Volume> getVolumes(Gerrit gerrit) {
    Set<Volume> volumes = new HashSet<>();

    volumes.add(GerritCluster.getGitRepositoriesVolume());
    volumes.add(GerritCluster.getLogsVolume());

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-init-config")
            .withNewConfigMap()
            .withName(GerritInitConfigMap.getName(gerrit))
            .endConfigMap()
            .build());

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-config")
            .withNewConfigMap()
            .withName(GerritConfigMap.getName(gerrit))
            .endConfigMap()
            .build());

    for (String secretName : gerrit.getSpec().getSecrets()) {
      volumes.add(
          new VolumeBuilder()
              .withName(secretName)
              .withNewSecret()
              .withSecretName(secretName)
              .endSecret()
              .build());
    }

    if (gerrit.getSpec().getStorage().getPluginCacheStorage().isEnabled()
        && gerrit.getSpec().getPlugins().stream().anyMatch(p -> !p.isPackagedPlugin())) {
      volumes.add(
          new VolumeBuilder()
              .withName("gerrit-plugin-cache")
              .withNewPersistentVolumeClaim()
              .withClaimName(PluginCachePVC.PLUGIN_CACHE_PVC_NAME)
              .endPersistentVolumeClaim()
              .build());
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumes.add(GerritCluster.getNfsImapdConfigVolume());
    }

    return volumes;
  }

  private Set<VolumeMount> getVolumeMounts(Gerrit gerrit, boolean isInitContainer) {
    Set<VolumeMount> volumeMounts = new HashSet<>();
    volumeMounts.add(
        new VolumeMountBuilder().withName(SITE_VOLUME_NAME).withMountPath("/var/gerrit").build());
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount());
    volumeMounts.add(GerritCluster.getLogsVolumeMount());
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-config")
            .withMountPath("/var/mnt/etc/config")
            .build());

    for (String secretName : gerrit.getSpec().getSecrets()) {
      volumeMounts.add(
          new VolumeMountBuilder()
              .withName(secretName)
              .withMountPath("/var/mnt/etc/secret")
              .build());
    }

    if (isInitContainer) {
      volumeMounts.add(
          new VolumeMountBuilder()
              .withName("gerrit-init-config")
              .withMountPath("/var/config")
              .build());

      if (gerrit.getSpec().getStorage().getPluginCacheStorage().isEnabled()
          && gerrit.getSpec().getPlugins().stream().anyMatch(p -> !p.isPackagedPlugin())) {
        volumeMounts.add(
            new VolumeMountBuilder()
                .withName("gerrit-plugin-cache")
                .withMountPath("/var/mnt/plugins")
                .build());
      }
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    return volumeMounts;
  }

  private List<ContainerPort> getContainerPorts(Gerrit gerrit) {
    List<ContainerPort> containerPorts = new ArrayList<>();
    containerPorts.add(new ContainerPort(HTTP_PORT, null, null, "http", null));

    if (gerrit.getSpec().getService().isSshEnabled()) {
      containerPorts.add(new ContainerPort(SSH_PORT, null, null, "ssh", null));
    }

    return containerPorts;
  }
}
