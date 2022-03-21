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

package com.google.gerrit.k8s.operator;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1beta1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1beta1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1beta1.JobTemplateSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class GitGcReconciler implements Reconciler<GitGc> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String GIT_REPOSITORIES_VOLUME_NAME = "git-repositories";
  private static final String LOGS_VOLUME_NAME = "logs";

  private final KubernetesClient kubernetesClient;

  public GitGcReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public UpdateControl<GitGc> reconcile(GitGc gitGc, Context context) {
    log.info("Reconciling GitGc with name: {}", gitGc.getMetadata().getName());
    String ns = gitGc.getMetadata().getNamespace();
    String name = gitGc.getMetadata().getName();
    CronJob existingGitGcCronJob =
        kubernetesClient.batch().v1beta1().cronjobs().inNamespace(ns).withName(name).get();
    if (existingGitGcCronJob == null) {
      log.info("Could not find existing GitGc with name {} in namespace {}", name, ns);
      createCronJob(gitGc);
    } else {
      log.info("Found existing GitGc with name {} in namespace {}. Updating.", name, ns);
      updateCronJob(gitGc, existingGitGcCronJob);
    }
    return UpdateControl.updateStatus(updateGitGcStatus(gitGc));
  }

  private GitGc updateGitGcStatus(GitGc gitGc) {
    GitGcStatus status = gitGc.getStatus();
    if (status == null) {
      status = new GitGcStatus();
    }
    status.setSpecifiedProjects(gitGc.getSpec().getProjects());
    gitGc.setStatus(status);
    return gitGc;
  }

  private void createCronJob(GitGc gitGc) {
    Map<String, String> gitGcLabels = new HashMap<>();
    gitGcLabels.put("app", "gerrit");
    gitGcLabels.put("component", "git-gc");

    EnvVarSource metaDataEnvSource = new EnvVarSource();
    metaDataEnvSource.setFieldRef(null);

    Volume gitRepositoriesVolume =
        new VolumeBuilder()
            .withName(GIT_REPOSITORIES_VOLUME_NAME)
            .withNewPersistentVolumeClaim()
            .withClaimName(gitGc.getSpec().getRepositoryPVC())
            .endPersistentVolumeClaim()
            .build();

    Volume logsVolume =
        new VolumeBuilder()
            .withName(LOGS_VOLUME_NAME)
            .withNewPersistentVolumeClaim()
            .withClaimName(gitGc.getSpec().getLogPVC())
            .endPersistentVolumeClaim()
            .build();

    JobTemplateSpec gitGcJobTemplate =
        new JobTemplateSpecBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .withAnnotations(Collections.singletonMap("sidecar.istio.io/inject", "false"))
            .withLabels(gitGcLabels)
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("OnFailure")
            .withNewSecurityContext()
            .withFsGroup(100L)
            .endSecurityContext()
            .addToContainers(buildGitGcContainer(gitGc))
            .withVolumes(List.of(gitRepositoriesVolume, logsVolume))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    CronJobBuilder gitGcCronJobBuilder = new CronJobBuilder().withApiVersion("batch/v1beta1");

    gitGcCronJobBuilder
        .withNewMetadata()
        .withNamespace(gitGc.getMetadata().getNamespace())
        .withName(gitGc.getMetadata().getName())
        .withLabels(gitGcLabels)
        .withAnnotations(
            Collections.singletonMap("app.kubernetes.io/managed-by", "gerrit-operator"))
        .endMetadata();

    gitGcCronJobBuilder
        .withNewSpec()
        .withSchedule(gitGc.getSpec().getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(gitGcJobTemplate)
        .endSpec();

    CronJob gitGcCronJob = gitGcCronJobBuilder.build();
    kubernetesClient
        .batch()
        .v1beta1()
        .cronjobs()
        .inNamespace(gitGc.getMetadata().getNamespace())
        .createOrReplace(gitGcCronJob);
  }

  private void updateCronJob(GitGc gitGc, CronJob existingGitGcCronJob) {
    existingGitGcCronJob.getSpec().setSchedule(gitGc.getSpec().getSchedule());

    Container gitGcContainer =
        existingGitGcCronJob
            .getSpec()
            .getJobTemplate()
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .get(0);

    gitGcContainer.setImage(gitGc.getSpec().getImage());

    Set<String> projects = gitGc.getSpec().getProjects();
    ArrayList<String> args = new ArrayList<>();
    if (!projects.isEmpty()) {
      for (String project : gitGc.getSpec().getProjects()) {
        args.add("-p");
        args.add(project);
      }
    }
    gitGcContainer.setArgs(args);

    gitGcContainer.setResources(gitGc.getSpec().getResources());
    kubernetesClient
        .batch()
        .v1beta1()
        .cronjobs()
        .inNamespace(gitGc.getMetadata().getNamespace())
        .createOrReplace(existingGitGcCronJob);
  }

  private Container buildGitGcContainer(GitGc gitGc) {
    VolumeMount gitRepositoriesVolumeMount =
        new VolumeMountBuilder()
            .withName(GIT_REPOSITORIES_VOLUME_NAME)
            .withMountPath("/var/gerrit/git")
            .build();

    VolumeMount logsVolumeMount =
        new VolumeMountBuilder()
            .withName(LOGS_VOLUME_NAME)
            .withSubPathExpr("git-gc/$(POD_NAME)")
            .withMountPath("/var/log/git")
            .build();

    EnvVar podNameEnvVar =
        new EnvVarBuilder()
            .withName("POD_NAME")
            .withNewValueFrom()
            .withNewFieldRef()
            .withFieldPath("metadata.name")
            .endFieldRef()
            .endValueFrom()
            .build();

    ContainerBuilder gitGcContainerBuilder =
        new ContainerBuilder()
            .withName("git-gc")
            .withImage(gitGc.getSpec().getImage())
            .withResources(gitGc.getSpec().getResources())
            .withEnv(podNameEnvVar)
            .withVolumeMounts(List.of(gitRepositoriesVolumeMount, logsVolumeMount));

    Set<String> projects = gitGc.getSpec().getProjects();
    if (!projects.isEmpty()) {
      for (String project : gitGc.getSpec().getProjects()) {
        gitGcContainerBuilder.addAllToArgs(List.of("-p", project));
      }
    }

    return gitGcContainerBuilder.build();
  }
}
