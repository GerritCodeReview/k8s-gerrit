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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public IncomingReplicationTaskCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {
    String ns = incomingReplTask.getMetadata().getNamespace();
    String name = incomingReplTask.getMetadata().getName();

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
            .addToContainers(buildTaskContainer(incomingReplTask))
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
        .withLabels(getLabels(incomingReplTask))
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
        name, getComponentName(name), GerritReconciler.class.getSimpleName());
  }

  private Container buildTaskContainer(IncomingReplicationTask incomingReplTask) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));

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
            .withEnv(GerritCluster.getPodNameEnvVar())
            .withVolumeMounts(volumeMounts);

    return taskContainerBuilder.build();
  }

  private List<Volume> getVolumes(IncomingReplicationTask incomingReplTask) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        GerritCluster.getSharedVolume(
            incomingReplTask.getSpec().getStorage().getSharedStorage().getExternalPVC()));

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
    return volumes;
  }
}
