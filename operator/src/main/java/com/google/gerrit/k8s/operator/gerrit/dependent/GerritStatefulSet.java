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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritModule;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritModuleData;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@KubernetesDependent
public class GerritStatefulSet
    extends CRUDReconcileAddKubernetesDependentResource<StatefulSet, Gerrit> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private static final String SITE_VOLUME_NAME = "gerrit-site";
  public static final int HTTP_PORT = 8080;
  public static final int SSH_PORT = 29418;
  public static final int JGROUPS_PORT = 7800;
  public static final int DEBUG_PORT = 8000;

  public GerritStatefulSet() {
    super(StatefulSet.class);
  }

  @Override
  protected StatefulSet desired(Gerrit gerrit, Context<Gerrit> context) {
    StatefulSetBuilder stsBuilder = new StatefulSetBuilder();

    List<Container> initContainers = new ArrayList<>();
    List<Container> sidecarContainers = new ArrayList<>();

    if (gerrit.getSpec().getFluentBitSidecar().isEnabled()) {
      Container fluentBitSidecarContainer =
          new ContainerBuilder()
              .withName("fluentbit-logger")
              .withEnv(getEnvVars(gerrit))
              .withImagePullPolicy(gerrit.getSpec().getContainerImages().getImagePullPolicy())
              .withImage(gerrit.getSpec().getFluentBitSidecar().getImage())
              .addAllToVolumeMounts(getFluentbitVolumeMounts(gerrit))
              .build();
      sidecarContainers.add(fluentBitSidecarContainer);
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      boolean hasIdmapdConfig =
          gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround().getIdmapdConfig()
              != null;
      ContainerImageConfig images = gerrit.getSpec().getContainerImages();

      if (gerrit.getSpec().isHighlyAvailablePrimary()) {

        initContainers.add(
            GerritCluster.createNfsInitContainer(
                hasIdmapdConfig, images, List.of(GerritCluster.getHAShareVolumeMount())));
      } else {
        initContainers.add(GerritCluster.createNfsInitContainer(hasIdmapdConfig, images));
      }
    }

    Map<String, String> replicaSetAnnotations = new HashMap<>();
    if (gerrit.getStatus() != null && isGerritRestartRequired(gerrit, context)) {
      replicaSetAnnotations.put(
          "kubectl.kubernetes.io/restartedAt", RFC3339.format(Timestamp.from(Instant.now())));
    } else {
      Optional<StatefulSet> existingSts = context.getSecondaryResource(StatefulSet.class);
      if (existingSts.isPresent()) {
        Map<String, String> existingAnnotations =
            existingSts.get().getSpec().getTemplate().getMetadata().getAnnotations();
        if (existingAnnotations.containsKey("kubectl.kubernetes.io/restartedAt")) {
          replicaSetAnnotations.put(
              "kubectl.kubernetes.io/restartedAt",
              existingAnnotations.get("kubectl.kubernetes.io/restartedAt"));
        }
      }
    }

    stsBuilder
        .withApiVersion("apps/v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withServiceName(new GerritHeadlessService().getName(gerrit))
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
        .withAnnotations(replicaSetAnnotations)
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withServiceAccount(gerrit.getSpec().getServiceAccount())
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
        .withEnv(getEnvVars(gerrit))
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
            "/bin/ash", "-c", "kill -2 $(pidof java) && tail --pid=$(pidof java) -f /dev/null")
        .endExec()
        .endPreStop()
        .endLifecycle()
        .withEnv(getEnvVars(gerrit))
        .withPorts(getContainerPorts(gerrit))
        .withResources(gerrit.getSpec().getResources())
        .withStartupProbe(gerrit.getSpec().getStartupProbe())
        .withReadinessProbe(gerrit.getSpec().getReadinessProbe())
        .withLivenessProbe(gerrit.getSpec().getLivenessProbe())
        .addAllToVolumeMounts(getVolumeMounts(gerrit, false))
        .endContainer()
        .addAllToContainers(sidecarContainers)
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

  public static String getName(Gerrit gerrit) {
    return getName(gerrit.getMetadata().getName());
  }

  public static String getName(String gerritName) {
    return gerritName;
  }

  private static String getComponentName(String gerritName) {
    return String.format("gerrit-statefulset-%s", gerritName);
  }

  public static Map<String, String> getSelectorLabels(Gerrit gerrit) {
    return getSelectorLabels(gerrit.getMetadata().getName());
  }

  public static Map<String, String> getSelectorLabels(String gerritName) {
    return GerritCluster.getSelectorLabels(gerritName, getComponentName(gerritName));
  }

  private static Map<String, String> getLabels(Gerrit gerrit) {
    String name = gerrit.getMetadata().getName();
    return GerritCluster.getLabels(
        name, getComponentName(name), GerritReconciler.class.getSimpleName());
  }

  private Set<Volume> getVolumes(Gerrit gerrit) {
    Set<Volume> volumes = new HashSet<>();

    if (OperatorContext.getClusterMode() == ClusterMode.HIGH_AVAILABILITY) {
      volumes.add(
          GerritCluster.getSharedVolume(
              gerrit.getSpec().getStorage().getSharedStorage().getExternalPVC()));
    }
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

    volumes.add(
        new VolumeBuilder()
            .withName(gerrit.getSpec().getSecretRef())
            .withNewSecret()
            .withSecretName(gerrit.getSpec().getSecretRef())
            .endSecret()
            .build());

    for (GerritModule module : gerrit.getSpec().getAllGerritModules()) {
      GerritModuleData md = module.getModuleData();
      if (md == null) {
        continue;
      }
      volumes.add(
          new VolumeBuilder()
              .withName(md.getSecretRef())
              .withNewSecret()
              .withSecretName(md.getSecretRef())
              .withDefaultMode(md.isExecutable() ? 0754 : 0644)
              .endSecret()
              .build());
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumes.add(GerritCluster.getNfsImapdConfigVolume());
    }

    if (gerrit.getSpec().getFluentBitSidecar().isEnabled()) {
      volumes.add(
          new VolumeBuilder()
              .withName(FluentBitConfigMap.getName(gerrit))
              .withNewConfigMap()
              .withName(FluentBitConfigMap.getName(gerrit))
              .endConfigMap()
              .build());
    }

    return volumes;
  }

  private Set<VolumeMount> getVolumeMounts(Gerrit gerrit, boolean isInitContainer) {
    Set<VolumeMount> volumeMounts = new HashSet<>();
    volumeMounts.add(
        new VolumeMountBuilder().withName(SITE_VOLUME_NAME).withMountPath("/var/gerrit").build());
    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      volumeMounts.add(GerritCluster.getHAShareVolumeMount());
    }
    if (OperatorContext.getClusterMode() == ClusterMode.HIGH_AVAILABILITY) {
      volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount());
    }
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-config")
            .withMountPath("/var/mnt/etc/config")
            .build());

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(gerrit.getSpec().getSecretRef())
            .withMountPath("/var/mnt/etc/secret")
            .build());

    if (isInitContainer) {
      volumeMounts.add(
          new VolumeMountBuilder()
              .withName("gerrit-init-config")
              .withMountPath("/var/config")
              .build());

      if (gerrit.getSpec().getStorage().getPluginCache().isEnabled()
          && gerrit.getSpec().getPlugins().stream().anyMatch(p -> !p.isPackagedPlugin())) {
        volumeMounts.add(GerritCluster.getPluginCacheVolumeMount());
      }
    }

    for (GerritModule module : gerrit.getSpec().getAllGerritModules()) {
      GerritModuleData md = module.getModuleData();
      if (md == null) {
        continue;
      }
      volumeMounts.add(
          new VolumeMountBuilder()
              .withName(md.getSecretRef())
              .withMountPath("/var/mnt/data/" + module.getName())
              .build());
    }

    NfsWorkaroundConfig nfsWorkaround =
        gerrit.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    return volumeMounts;
  }

  private Set<VolumeMount> getFluentbitVolumeMounts(Gerrit gerrit) {
    Set<VolumeMount> volumeMounts = new HashSet<>();
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(FluentBitConfigMap.getName(gerrit))
            .withMountPath("/fluent-bit/etc/")
            .build());

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(SITE_VOLUME_NAME)
            .withSubPath("logs")
            .withMountPath("/var/mnt/logs")
            .build());

    return volumeMounts;
  }

  private List<ContainerPort> getContainerPorts(Gerrit gerrit) {
    List<ContainerPort> containerPorts = new ArrayList<>();
    containerPorts.add(new ContainerPort(HTTP_PORT, null, null, "http", null));

    if (gerrit.isSshEnabled()) {
      containerPorts.add(new ContainerPort(SSH_PORT, null, null, "ssh", null));
    }

    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      containerPorts.add(new ContainerPort(JGROUPS_PORT, null, null, "jgroups", null));
    }

    if (gerrit.getSpec().getDebug().isEnabled()) {
      containerPorts.add(new ContainerPort(DEBUG_PORT, null, null, "debug", null));
    }

    return containerPorts;
  }

  private List<EnvVar> getEnvVars(Gerrit gerrit) {
    List<EnvVar> envVars = new ArrayList<>();
    envVars.add(GerritCluster.getPodNameEnvVar());
    envVars.addAll(gerrit.getSpec().getEnvVars());
    if (OperatorContext.getClusterMode() == ClusterMode.MULTISITE) {
      envVars.add(
          GerritCluster.getReplicasNumEnvVar(Integer.toString(gerrit.getSpec().getReplicas())));
    }

    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      envVars.add(
          new EnvVarBuilder()
              .withName("GERRIT_URL")
              .withValue(
                  String.format(
                      "http://$(POD_NAME).%s:%s",
                      new GerritHeadlessService().getHostname(gerrit), HTTP_PORT))
              .build());
    }
    return envVars;
  }

  private boolean isGerritRestartRequired(Gerrit gerrit, Context<Gerrit> context) {
    if (wasConfigMapUpdated(GerritInitConfigMap.getName(gerrit), gerrit, context)
        || wasConfigMapUpdated(GerritConfigMap.getName(gerrit), gerrit, context)) {
      return true;
    }

    return wasSecretUpdated(gerrit, context);
  }

  private boolean wasConfigMapUpdated(
      String configMapName, Gerrit gerrit, Context<Gerrit> context) {
    String configMapVersion =
        context
            .getClient()
            .configMaps()
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(configMapName)
            .get()
            .getMetadata()
            .getResourceVersion();
    String knownConfigMapVersion =
        gerrit.getStatus().getAppliedConfigMapVersions().get(configMapName);
    if (!configMapVersion.equals(knownConfigMapVersion)) {
      logger.atInfo().log(
          "Looking up ConfigMap: %s; Installed configmap resource version: %s; Resource version known to Gerrit: %s",
          configMapName, configMapVersion, knownConfigMapVersion);
      return true;
    }
    return false;
  }

  public boolean wasSecretUpdated(Gerrit gerrit, Context<Gerrit> context) {
    String secretName = gerrit.getSpec().getSecretRef();
    Optional<Secret> gerritSecret =
        Optional.ofNullable(
            context
                .getClient()
                .secrets()
                .inNamespace(gerrit.getMetadata().getNamespace())
                .withName(secretName)
                .get());
    if (gerritSecret.isPresent()) {
      String secVersion = gerritSecret.get().getMetadata().getResourceVersion();
      if (!secVersion.equals(gerrit.getStatus().getAppliedSecretVersions().get(secretName))) {
        logger.atFine().log(
            "Looking up Secret: %s; Installed secret resource version: %s; Resource version known to Gerrit: %s",
            secretName, secVersion, gerrit.getStatus().getAppliedSecretVersions().get(secretName));
        return true;
      }
    }

    return wasModuleDataSecretUpdated(gerrit, context);
  }

  public boolean wasModuleDataSecretUpdated(Gerrit gerrit, Context<Gerrit> context) {
    for (String secretName : gerrit.getModuleDataSecretNames()) {
      String appliedVersion = gerrit.getStatus().getAppliedSecretVersions().get(secretName);
      String actualVersion =
          context
              .getClient()
              .secrets()
              .inNamespace(gerrit.getMetadata().getNamespace())
              .withName(secretName)
              .get()
              .getMetadata()
              .getResourceVersion();
      if (!actualVersion.equals(appliedVersion)) {
        return true;
      }
    }
    return false;
  }
}
