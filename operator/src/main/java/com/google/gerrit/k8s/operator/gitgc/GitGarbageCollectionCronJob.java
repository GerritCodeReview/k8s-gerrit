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

package com.google.gerrit.k8s.operator.gitgc;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GitGarbageCollectionCronJob
    extends CRUDKubernetesDependentResource<CronJob, GitGarbageCollection> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GitGarbageCollectionCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    String ns = gitGc.getMetadata().getNamespace();
    String name = gitGc.getMetadata().getName();
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(ns)
            .withName(gitGc.getSpec().getCluster())
            .get();

    if (gerritCluster == null) {
      throw new IllegalStateException("The Gerrit cluster could not be found.");
    }
    logger.atInfo().log("Reconciling GitGc with name: %s/%s", ns, name);

    Map<String, String> gitGcLabels =
        gerritCluster.getLabels("GitGc", this.getClass().getSimpleName());

    EnvVarSource metaDataEnvSource = new EnvVarSource();
    metaDataEnvSource.setFieldRef(null);

    List<Container> initContainers = new ArrayList<>();
    List<Volume> volumes =
        List.of(gerritCluster.getGitRepositoriesVolume(), gerritCluster.getLogsVolume());

    if (gerritCluster.getSpec().getStorageClasses().isEnableNfsWorkaround()) {
      initContainers.add(createNfsImapdInitContainer(gerritCluster));
      if (gerritCluster.getSpec().getStorageClasses().getIdmapdConfig() != null) {
        volumes.add(gerritCluster.getNfsImapdConfigVolume());
      }
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
            .withLabels(gitGcLabels)
            .endMetadata()
            .withNewSpec()
            .withTolerations(gitGc.getSpec().getTolerations())
            .withAffinity(gitGc.getSpec().getAffinity())
            .addAllToImagePullSecrets(gerritCluster.getSpec().getImagePullSecrets())
            .withRestartPolicy("OnFailure")
            .withNewSecurityContext()
            .withFsGroup(100L)
            .endSecurityContext()
            .addToContainers(buildGitGcContainer(gitGc, gerritCluster))
            .withVolumes(volumes)
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    return new CronJobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withNamespace(ns)
        .withName(name)
        .withLabels(gitGcLabels)
        .withAnnotations(
            Collections.singletonMap("app.kubernetes.io/managed-by", "gerrit-operator"))
        .addNewOwnerReference()
        .withApiVersion(gitGc.getApiVersion())
        .withKind(gitGc.getKind())
        .withName(name)
        .withUid(gitGc.getMetadata().getUid())
        .endOwnerReference()
        .endMetadata()
        .withNewSpec()
        .withSchedule(gitGc.getSpec().getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(gitGcJobTemplate)
        .endSpec()
        .build();
  }

  private Container createNfsImapdInitContainer(GerritCluster gerritCluster) {
    List<VolumeMount> volumeMounts = List.of(gerritCluster.getLogsVolumeMount());

    if (gerritCluster.getSpec().getStorageClasses().getIdmapdConfig() != null) {
      volumeMounts.add(gerritCluster.getNfsImapdConfigVolumeMount());
    }

    return new ContainerBuilder()
        .withName("nfs-init")
        .withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
        .withImage(gerritCluster.getSpec().getBusyBox().getBusyBoxImage())
        .withCommand(List.of("sh", "-c"))
        .withArgs("chown -R 1000:100 /var/mnt/logs")
        .withEnv(getPodNameEnvVar())
        .withVolumeMounts(volumeMounts)
        .build();
  }

  private static EnvVar getPodNameEnvVar() {
    return new EnvVarBuilder()
        .withName("POD_NAME")
        .withNewValueFrom()
        .withNewFieldRef()
        .withFieldPath("metadata.name")
        .endFieldRef()
        .endValueFrom()
        .build();
  }

  private Container buildGitGcContainer(GitGarbageCollection gitGc, GerritCluster gerritCluster) {
    List<VolumeMount> volumeMounts =
        List.of(gerritCluster.getGitRepositoriesVolumeMount(), gerritCluster.getLogsVolumeMount());

    if (gerritCluster.getSpec().getStorageClasses().isEnableNfsWorkaround()
        && gerritCluster.getSpec().getStorageClasses().getIdmapdConfig() != null) {
      volumeMounts.add(gerritCluster.getNfsImapdConfigVolumeMount());
    }

    ContainerBuilder gitGcContainerBuilder =
        new ContainerBuilder()
            .withName("git-gc")
            .withImagePullPolicy(gerritCluster.getSpec().getImagePullPolicy())
            .withImage(gitGc.getSpec().getImage())
            .withResources(gitGc.getSpec().getResources())
            .withEnv(getPodNameEnvVar())
            .withVolumeMounts(volumeMounts);

    ArrayList<String> args = new ArrayList<>();
    for (String project : gitGc.getSpec().getProjects()) {
      args.add("-p");
      args.add(project);
    }
    for (String project : gitGc.getStatus().getExcludedProjects()) {
      args.add("-s");
      args.add(project);
    }
    gitGcContainerBuilder.addAllToArgs(args);

    return gitGcContainerBuilder.build();
  }
}
