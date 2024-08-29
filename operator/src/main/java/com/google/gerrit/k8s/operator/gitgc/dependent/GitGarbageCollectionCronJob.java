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

package com.google.gerrit.k8s.operator.gitgc.dependent;

import static com.google.gerrit.k8s.operator.Constants.GERRIT_USER_GROUP_ID;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GitGarbageCollectionCronJob
    extends CRUDReconcileAddKubernetesDependentResource<CronJob, GitGarbageCollection> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GitGarbageCollectionCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    String ns = gitGc.getMetadata().getNamespace();
    String name = gitGc.getMetadata().getName();
    GerritCluster gerritCluster =
        context
            .getClient()
            .resources(GerritCluster.class)
            .inNamespace(ns)
            .withName(gitGc.getSpec().getCluster())
            .get();
    logger.atInfo().log("Reconciling GitGc with name: %s/%s", ns, name);

    Map<String, String> gitGcLabels =
        gerritCluster.getLabels("GitGc", this.getClass().getSimpleName());

    List<Container> initContainers = new ArrayList<>();
    if (gerritCluster.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled()
        && gerritCluster
            .getSpec()
            .getStorage()
            .getStorageClasses()
            .getNfsWorkaround()
            .isChownOnStartup()) {
      initContainers.add(gerritCluster.createNfsInitContainer());
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
            .addAllToImagePullSecrets(
                gerritCluster.getSpec().getContainerImages().getImagePullSecrets())
            .withRestartPolicy("OnFailure")
            .withNewSecurityContext()
            .withFsGroup(GERRIT_USER_GROUP_ID)
            .endSecurityContext()
            .addAllToInitContainers(initContainers)
            .addToContainers(buildGitGcContainer(gitGc, gerritCluster))
            .withVolumes(getVolumes(gerritCluster))
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

  private Container buildGitGcContainer(GitGarbageCollection gitGc, GerritCluster gerritCluster) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));

    if (gerritCluster.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled()
        && gerritCluster
                .getSpec()
                .getStorage()
                .getStorageClasses()
                .getNfsWorkaround()
                .getIdmapdConfig()
            != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    ContainerBuilder gitGcContainerBuilder =
        new ContainerBuilder()
            .withName("git-gc")
            .withImagePullPolicy(gerritCluster.getSpec().getContainerImages().getImagePullPolicy())
            .withImage(
                gerritCluster
                    .getSpec()
                    .getContainerImages()
                    .getGerritImages()
                    .getFullImageName("git-gc"))
            .withResources(gitGc.getSpec().getResources())
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
    if (gitGc.getSpec().isDisableBitmapIndex()) {
      args.add("-B");
    }
    if (gitGc.getSpec().isDisablePackRefs()) {
      args.add("-R");
    }
    if (gitGc.getSpec().isPreservePacks()) {
      args.add("-P");
    }
    gitGcContainerBuilder.addAllToArgs(args);

    return gitGcContainerBuilder.build();
  }

  private List<Volume> getVolumes(GerritCluster gerritCluster) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        GerritCluster.getSharedVolume(
            gerritCluster.getSpec().getStorage().getSharedStorage().getExternalPVC()));

    if (gerritCluster.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled()) {
      if (gerritCluster
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
