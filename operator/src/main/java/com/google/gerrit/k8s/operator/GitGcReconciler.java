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
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class GitGcReconciler
    implements Reconciler<GitGc>, EventSourceInitializer<GitGc>, Cleaner<GitGc> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String GIT_REPOSITORIES_VOLUME_NAME = "git-repositories";
  private static final String LOGS_VOLUME_NAME = "logs";

  private final KubernetesClient kubernetesClient;

  public GitGcReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GitGc> context) {
    final SecondaryToPrimaryMapper<GitGc> specificProjectGitGcMapper =
        (GitGc gc) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getProjects().isEmpty())
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GitGc, GitGc> eventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GitGc.class, context)
                .withSecondaryToPrimaryMapper(specificProjectGitGcMapper)
                .build(),
            context);
    return EventSourceInitializer.nameEventSources(eventSource);
  }

  @Override
  public UpdateControl<GitGc> reconcile(GitGc gitGc, Context<GitGc> context) {
    String ns = gitGc.getMetadata().getNamespace();
    String name = gitGc.getMetadata().getName();
    log.info("Reconciling GitGc with name: {}/{}", ns, name);

    validateGitGCProjectList(gitGc);
    gitGc = excludeProjectsInExistingGCs(gitGc);

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
    status.setReplicateAll(gitGc.getSpec().getProjects().isEmpty());
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

    ArrayList<String> args = new ArrayList<>();
    for (String project : gitGc.getSpec().getProjects()) {
      args.add("-p");
      args.add(project);
    }
    for (String project : gitGc.getStatus().getExcludedProjects()) {
      args.add("-s");
      args.add(project);
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

  private GitGc excludeProjectsInExistingGCs(GitGc currentGitGc) {
    if (!currentGitGc.getSpec().getProjects().isEmpty()) {
      return currentGitGc;
    }

    List<GitGc> gitGcs =
        kubernetesClient
            .resources(GitGc.class)
            .inNamespace(currentGitGc.getMetadata().getNamespace())
            .list()
            .getItems();
    gitGcs.remove(currentGitGc);
    GitGcStatus currentGitGcStatus = currentGitGc.getStatus();
    for (GitGc gc : gitGcs) {
      currentGitGcStatus.excludeProjects(gc.getSpec().getProjects());
    }
    currentGitGc.setStatus(currentGitGcStatus);

    return currentGitGc;
  }

  private void validateGitGCProjectList(GitGc gitGc) {
    Set<String> projects = gitGc.getSpec().getProjects();
    List<GitGc> gitGcs =
        kubernetesClient
            .resources(GitGc.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .list()
            .getItems();
    gitGcs.remove(gitGc);
    List<GitGc> allProjectGcs =
        gitGcs.stream().filter(gc -> gc.getStatus().isReplicateAll()).collect(Collectors.toList());
    if (!allProjectGcs.isEmpty() && projects.isEmpty()) {
      throw new IllegalStateException(
          "Multiple Git GC jobs working on all projects are not allowed.");
    }

    Set<String> projectsWithExistingGC =
        gitGcs.stream()
            .map(gc -> gc.getSpec().getProjects())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    Set<String> projectsIntercept = new HashSet<>();
    projectsIntercept.addAll(projects);
    projectsIntercept.retainAll(projectsWithExistingGC);
    if (!projectsIntercept.isEmpty()) {
      throw new IllegalStateException(
          String.format("Git GC already configured for projects: %s.", projectsIntercept));
    }
  }

  @Override
  public DeleteControl cleanup(GitGc gitGc, Context<GitGc> context) {
    kubernetesClient
        .batch()
        .v1beta1()
        .cronjobs()
        .inNamespace(gitGc.getMetadata().getNamespace())
        .withName(gitGc.getMetadata().getName())
        .delete();
    return DeleteControl.defaultDelete();
  }
}
