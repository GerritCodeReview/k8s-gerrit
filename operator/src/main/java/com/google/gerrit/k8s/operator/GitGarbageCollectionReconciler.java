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
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class GitGarbageCollectionReconciler
    implements Reconciler<GitGarbageCollection>, Cleaner<GitGarbageCollection> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;

  public GitGarbageCollectionReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public UpdateControl<GitGarbageCollection> reconcile(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    log.info(
        "Reconciling GitGc with name: {}/{}",
        gitGc.getMetadata().getNamespace(),
        gitGc.getMetadata().getName());

    createCronJob(gitGc);
    return UpdateControl.updateStatus(gitGc);
  }

  private void createCronJob(GitGarbageCollection gitGc) {
    Map<String, String> gitGcLabels = new HashMap<>();
    gitGcLabels.put("app", "gerrit");
    gitGcLabels.put("component", "git-gc");

    EnvVarSource metaDataEnvSource = new EnvVarSource();
    metaDataEnvSource.setFieldRef(null);

    EnvVar podNameEnvVar =
        new EnvVarBuilder()
            .withName("POD_NAME")
            .withNewValueFrom()
            .withNewFieldRef()
            .withFieldPath("metadata.name")
            .endFieldRef()
            .endValueFrom()
            .build();

    String gitRepositoriesVolumeName = "git-repositories";
    Volume gitRepositoriesVolume =
        new VolumeBuilder()
            .withName(gitRepositoriesVolumeName)
            .withNewPersistentVolumeClaim()
            .withClaimName(gitGc.getSpec().getRepositoryPVC())
            .endPersistentVolumeClaim()
            .build();

    VolumeMount gitRepositoriesVolumeMount =
        new VolumeMountBuilder()
            .withName(gitRepositoriesVolumeName)
            .withMountPath("/var/gerrit/git")
            .build();

    String logsVolumeName = "logs";
    Volume logsVolume =
        new VolumeBuilder()
            .withName(logsVolumeName)
            .withNewPersistentVolumeClaim()
            .withClaimName(gitGc.getSpec().getLogPVC())
            .endPersistentVolumeClaim()
            .build();

    VolumeMount logsVolumeMount =
        new VolumeMountBuilder()
            .withName(logsVolumeName)
            .withSubPathExpr("git-gc/$(POD_NAME)")
            .withMountPath("/var/log/git")
            .build();

    Container gitGcContainer =
        new ContainerBuilder()
            .withName("git-gc")
            .withImage(gitGc.getSpec().getImage())
            .withResources(gitGc.getSpec().getResources())
            .withEnv(podNameEnvVar)
            .withVolumeMounts(List.of(gitRepositoriesVolumeMount, logsVolumeMount))
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
            .addToContainers(gitGcContainer)
            .withVolumes(List.of(gitRepositoriesVolume, logsVolume))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    CronJobBuilder gitGcCronJobBuilder = new CronJobBuilder().withApiVersion("batch/v1");

    gitGcCronJobBuilder
        .withNewMetadata()
        .withNamespace(gitGc.getMetadata().getNamespace())
        .withName(gitGc.getMetadata().getName())
        .withLabels(gitGcLabels)
        .withAnnotations(
            Collections.singletonMap("app.kubernetes.io/managed-by", "gerrit-operator"))
        .addNewOwnerReference()
        .withApiVersion(gitGc.getApiVersion())
        .withKind(gitGc.getKind())
        .withName(gitGc.getMetadata().getName())
        .withUid(gitGc.getMetadata().getUid())
        .endOwnerReference()
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
        .v1()
        .cronjobs()
        .inNamespace(gitGc.getMetadata().getNamespace())
        .createOrReplace(gitGcCronJob);
  }

  @Override
  public DeleteControl cleanup(GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    kubernetesClient
        .batch()
        .v1()
        .cronjobs()
        .inNamespace(gitGc.getMetadata().getNamespace())
        .withName(gitGc.getMetadata().getName())
        .delete();
    return DeleteControl.defaultDelete();
  }
}
