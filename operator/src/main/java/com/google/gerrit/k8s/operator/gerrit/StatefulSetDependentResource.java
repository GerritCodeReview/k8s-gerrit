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

package com.google.gerrit.k8s.operator.gerrit;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberDependentResource;
import com.google.gerrit.k8s.operator.cluster.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.cluster.PluginCachePVC;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@KubernetesDependent
public class StatefulSetDependentResource
    extends GerritClusterMemberDependentResource<StatefulSet, Gerrit> {

  private static final String SITE_VOLUME_NAME = "gerrit-site";
  public static final int HTTP_PORT = 8080;
  public static final int SSH_PORT = 29418;

  public StatefulSetDependentResource() {
    super(StatefulSet.class);
  }

  @Override
  protected StatefulSet desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster = getGerritCluster(gerrit);

    StatefulSetBuilder stsBuilder = new StatefulSetBuilder();

    List<Container> initContainers = new ArrayList<>();

    NfsWorkaroundConfig nfsWorkaround =
        gerritCluster.getSpec().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      initContainers.add(gerritCluster.createNfsInitContainer());
    }

    stsBuilder
        .withApiVersion("apps/v1")
        .withNewMetadata()
        .withName(gerrit.getMetadata().getName())
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerritCluster, gerrit))
        .endMetadata()
        .withNewSpec()
        .withServiceName(ServiceDependentResource.getName(gerrit))
        .withReplicas(gerrit.getSpec().getReplicas())
        .withNewUpdateStrategy()
        .withNewRollingUpdate()
        .withPartition(gerrit.getSpec().getUpdatePartition())
        .endRollingUpdate()
        .endUpdateStrategy()
        .withNewSelector()
        .withMatchLabels(getSelectorLabels(gerritCluster, gerrit))
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(getLabels(gerritCluster, gerrit))
        .endMetadata()
        .withNewSpec()
        .withTolerations(gerrit.getSpec().getTolerations())
        .withTopologySpreadConstraints(gerrit.getSpec().getTopologySpreadConstraints())
        .withAffinity(gerrit.getSpec().getAffinity())
        .withPriorityClassName(gerrit.getSpec().getPriorityClassName())
        .withTerminationGracePeriodSeconds(gerrit.getSpec().getGracefulStopTimeout())
        .addAllToImagePullSecrets(gerritCluster.getSpec().getImagePullSecrets())
        .withNewSecurityContext()
        .withFsGroup(100L)
        .endSecurityContext()
        .addNewInitContainer()
        .withName("gerrit-init")
        .withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
        .withImage(gerritCluster.getSpec().getGerritImages().getFullImageName("gerrit-init"))
        .withResources(gerrit.getSpec().getResources())
        .addAllToVolumeMounts(getVolumeMounts(gerrit, gerritCluster, true))
        .endInitContainer()
        .addAllToInitContainers(initContainers)
        .addNewContainer()
        .withName("gerrit")
        .withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
        .withImage(gerritCluster.getSpec().getGerritImages().getFullImageName("gerrit"))
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
        .addAllToVolumeMounts(getVolumeMounts(gerrit, gerritCluster, false))
        .endContainer()
        .addAllToVolumes(getVolumes(gerrit, gerritCluster))
        .endSpec()
        .endTemplate()
        .addNewVolumeClaimTemplate()
        .withNewMetadata()
        .withName(SITE_VOLUME_NAME)
        .withLabels(getSelectorLabels(gerritCluster, gerrit))
        .endMetadata()
        .withNewSpec()
        .withAccessModes("ReadWriteOnce")
        .withNewResources()
        .withRequests(Map.of("storage", gerrit.getSpec().getSite().getSize()))
        .endResources()
        .withStorageClassName(gerritCluster.getSpec().getStorageClasses().getReadWriteOnce())
        .endSpec()
        .endVolumeClaimTemplate()
        .endSpec();

    return stsBuilder.build();
  }

  private static String getComponentName(Gerrit gerrit) {
    return String.format("gerrit-statefulset-%s", gerrit.getMetadata().getName());
  }

  public static Map<String, String> getSelectorLabels(GerritCluster gerritCluster, Gerrit gerrit) {
    return gerritCluster.getSelectorLabels(getComponentName(gerrit));
  }

  private static Map<String, String> getLabels(GerritCluster gerritCluster, Gerrit gerrit) {
    return gerritCluster.getLabels(
        getComponentName(gerrit), GerritReconciler.class.getSimpleName());
  }

  private Set<Volume> getVolumes(Gerrit gerrit, GerritCluster gerritCluster) {
    Set<Volume> volumes = new HashSet<>();

    volumes.add(gerritCluster.getGitRepositoriesVolume());
    volumes.add(gerritCluster.getLogsVolume());

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-init-config")
            .withNewConfigMap()
            .withName(GerritInitConfigMapDependentResource.getName(gerrit))
            .endConfigMap()
            .build());

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-config")
            .withNewConfigMap()
            .withName(GerritConfigMapDependentResource.getName(gerrit))
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

    if (gerritCluster.getSpec().getPluginCacheStorage().isEnabled()
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
        gerritCluster.getSpec().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumes.add(gerritCluster.getNfsImapdConfigVolume());
    }

    return volumes;
  }

  private Set<VolumeMount> getVolumeMounts(
      Gerrit gerrit, GerritCluster gerritCluster, boolean isInitContainer) {
    Set<VolumeMount> volumeMounts = new HashSet<>();
    volumeMounts.add(
        new VolumeMountBuilder().withName(SITE_VOLUME_NAME).withMountPath("/var/gerrit").build());
    volumeMounts.add(gerritCluster.getGitRepositoriesVolumeMount());
    volumeMounts.add(gerritCluster.getLogsVolumeMount());
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

      if (gerritCluster.getSpec().getPluginCacheStorage().isEnabled()
          && gerrit.getSpec().getPlugins().stream().anyMatch(p -> !p.isPackagedPlugin())) {
        volumeMounts.add(
            new VolumeMountBuilder()
                .withName("gerrit-plugin-cache")
                .withMountPath("/var/mnt/plugins")
                .build());
      }
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerritCluster.getSpec().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumeMounts.add(gerritCluster.getNfsImapdConfigVolumeMount());
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
