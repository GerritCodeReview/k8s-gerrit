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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import static com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent.IncomingReplicationTaskConfigMap.CONFIG_FILE_NAME;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.IncomingReplicationTaskReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomingReplicationTaskCronJob
    extends CRUDReconcileAddKubernetesDependentResource<CronJob, IncomingReplicationTask> {
  private static final String CONFIGMAP_VOLUME_NAME = "incoming-repl-config";

  public IncomingReplicationTaskCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {
    String ns = incomingReplTask.getMetadata().getNamespace();
    String name = incomingReplTask.getMetadata().getName();
    Map<String, String> labels = getLabels(incomingReplTask);

    List<Container> initContainers = new ArrayList<>();
    NfsWorkaroundConfig nfsWorkaround =
        incomingReplTask.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      boolean hasIdmapdConfig =
          incomingReplTask
                  .getSpec()
                  .getStorage()
                  .getStorageClasses()
                  .getNfsWorkaround()
                  .getIdmapdConfig()
              != null;
      ContainerImageConfig images = incomingReplTask.getSpec().getContainerImages();

      initContainers.add(GerritCluster.createNfsInitContainer(hasIdmapdConfig, images));
    }

    JobTemplateSpec gitGcJobTemplate =
        new JobTemplateSpecBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .withAnnotations(
                Map.of(
                    "sidecar.istio.io/inject",
                    "false",
                    "cluster-autoscaler.kubernetes.io/safe-to-evict",
                    "false"))
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withTolerations(incomingReplTask.getSpec().getTolerations())
            .withAffinity(incomingReplTask.getSpec().getAffinity())
            .addAllToImagePullSecrets(
                incomingReplTask.getSpec().getContainerImages().getImagePullSecrets())
            .withRestartPolicy("OnFailure")
            .withNewSecurityContext()
            .withFsGroup(100L)
            .endSecurityContext()
            .addAllToInitContainers(initContainers)
            .addToContainers(buildTaskContainer(incomingReplTask, context))
            .withVolumes(getVolumes(incomingReplTask))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    return new CronJobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withNamespace(ns)
        .withName(name)
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withSchedule(incomingReplTask.getSpec().getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(gitGcJobTemplate)
        .endSpec()
        .build();
  }

  private static String getComponentName(String incomingReplTaskName) {
    return String.format("task-incoming-replication-%s", incomingReplTaskName);
  }

  private static Map<String, String> getLabels(IncomingReplicationTask incomingReplTask) {
    String name = incomingReplTask.getMetadata().getName();
    return GerritCluster.getLabels(
        name, getComponentName(name), IncomingReplicationTaskReconciler.class.getSimpleName());
  }

  private Container buildTaskContainer(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {

    ContainerBuilder taskContainerBuilder =
        new ContainerBuilder()
            .withName("incoming-replication")
            .withImagePullPolicy(
                incomingReplTask.getSpec().getContainerImages().getImagePullPolicy())
            .withImage(
                incomingReplTask
                    .getSpec()
                    .getContainerImages()
                    .getGerritImages()
                    .getFullImageName("fetch-job"))
            .withResources(incomingReplTask.getSpec().getResources())
            .withVolumeMounts(getVolumeMounts(incomingReplTask, context));

    return taskContainerBuilder.build();
  }

  private List<VolumeMount> getVolumeMounts(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(CONFIGMAP_VOLUME_NAME)
            .withSubPath(CONFIG_FILE_NAME)
            .withMountPath("/var/gerrit/etc/" + CONFIG_FILE_NAME)
            .build());

    if (incomingReplTask.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled()
        && incomingReplTask
                .getSpec()
                .getStorage()
                .getStorageClasses()
                .getNfsWorkaround()
                .getIdmapdConfig()
            != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    String secretRef = incomingReplTask.getSpec().getSecretRef();
    if (secretRef != null && !secretRef.isBlank()) {
      Secret secret =
          context
              .getClient()
              .resources(Secret.class)
              .inNamespace(incomingReplTask.getMetadata().getNamespace())
              .withName(secretRef)
              .get();
      if (secret != null && secret.getData().containsKey(".netrc")) {
        volumeMounts.add(
            new VolumeMountBuilder()
                .withName(secretRef)
                .withSubPath(".netrc")
                .withMountPath("/home/gerrit/.netrc")
                .build());
      }
    }
    return volumeMounts;
  }

  private List<Volume> getVolumes(IncomingReplicationTask incomingReplTask) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        GerritCluster.getSharedVolume(
            incomingReplTask.getSpec().getStorage().getSharedStorage().getExternalPVC()));

    volumes.add(
        new VolumeBuilder()
            .withName(CONFIGMAP_VOLUME_NAME)
            .withNewConfigMap()
            .withName(IncomingReplicationTaskConfigMap.getName(incomingReplTask))
            .endConfigMap()
            .build());

    if (incomingReplTask
        .getSpec()
        .getStorage()
        .getStorageClasses()
        .getNfsWorkaround()
        .isEnabled()) {
      if (incomingReplTask
              .getSpec()
              .getStorage()
              .getStorageClasses()
              .getNfsWorkaround()
              .getIdmapdConfig()
          != null) {
        volumes.add(GerritCluster.getNfsImapdConfigVolume());
      }
    }

    String secretRef = incomingReplTask.getSpec().getSecretRef();
    if (secretRef != null && !secretRef.isBlank()) {
      volumes.add(
          new VolumeBuilder()
              .withName(secretRef)
              .withNewSecret()
              .withSecretName(secretRef)
              .endSecret()
              .build());
    }

    return volumes;
  }
}
