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

import static com.google.gerrit.k8s.operator.site.GerritSiteReconciler.REPOSITORY_PVC_NAME;

import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionStatus.GitGcState;
import com.google.gerrit.k8s.operator.site.GerritSite;
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
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class GitGarbageCollectionReconciler
    implements Reconciler<GitGarbageCollection>,
        EventSourceInitializer<GitGarbageCollection>,
        ErrorStatusHandler<GitGarbageCollection>,
        Cleaner<GitGarbageCollection> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String GIT_REPOSITORIES_VOLUME_NAME = "git-repositories";
  private static final String LOGS_VOLUME_NAME = "logs";

  private final KubernetesClient kubernetesClient;

  public GitGarbageCollectionReconciler(KubernetesClient client) {
    this.kubernetesClient = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GitGarbageCollection> context) {
    final SecondaryToPrimaryMapper<GitGarbageCollection> specificProjectGitGcMapper =
        (GitGarbageCollection gc) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getProjects().isEmpty())
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GitGarbageCollection, GitGarbageCollection> gitGcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GitGarbageCollection.class, context)
                .withSecondaryToPrimaryMapper(specificProjectGitGcMapper)
                .build(),
            context);

    final SecondaryToPrimaryMapper<GerritSite> gerritSiteMapper =
        (GerritSite site) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getSite().equals(site.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritSite, GitGarbageCollection> gerritSiteEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritSite.class, context)
                .withSecondaryToPrimaryMapper(gerritSiteMapper)
                .build(),
            context);

    return EventSourceInitializer.nameEventSources(gitGcEventSource, gerritSiteEventSource);
  }

  @Override
  public UpdateControl<GitGarbageCollection> reconcile(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    String ns = gitGc.getMetadata().getNamespace();
    String name = gitGc.getMetadata().getName();

    GerritSite gerritSite =
        kubernetesClient
            .resources(GerritSite.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .withName(gitGc.getSpec().getSite())
            .get();
    if (gerritSite == null) {
      throw new IllegalStateException("The Gerrit site could not be found.");
    }

    log.info("Reconciling GitGc with name: {}/{}", ns, name);

    validateGitGCProjectList(gitGc);
    if (gitGc.getSpec().getProjects().isEmpty()) {
      gitGc = excludeProjectsInExistingGCs(gitGc);
    }

    createCronJob(gitGc, gerritSite);
    return UpdateControl.updateStatus(updateGitGcStatus(gitGc));
  }

  private GitGarbageCollection updateGitGcStatus(GitGarbageCollection gitGc) {
    GitGarbageCollectionStatus status = gitGc.getStatus();
    if (status == null) {
      status = new GitGarbageCollectionStatus();
    }
    status.setReplicateAll(gitGc.getSpec().getProjects().isEmpty());
    status.setState(GitGcState.ACTIVE);
    gitGc.setStatus(status);
    return gitGc;
  }

  private void createCronJob(GitGarbageCollection gitGc, GerritSite gerritSite) {
    Map<String, String> gitGcLabels =
        gerritSite.getLabels("GitGc", this.getClass().getSimpleName());

    EnvVarSource metaDataEnvSource = new EnvVarSource();
    metaDataEnvSource.setFieldRef(null);

    Volume gitRepositoriesVolume =
        new VolumeBuilder()
            .withName(GIT_REPOSITORIES_VOLUME_NAME)
            .withNewPersistentVolumeClaim()
            .withClaimName(REPOSITORY_PVC_NAME)
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
    kubernetesClient.resource(gitGcCronJob).createOrReplace();
  }

  private Container buildGitGcContainer(GitGarbageCollection gitGc) {
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

  private GitGarbageCollection excludeProjectsInExistingGCs(GitGarbageCollection currentGitGc) {
    List<GitGarbageCollection> gitGcs =
        kubernetesClient
            .resources(GitGarbageCollection.class)
            .inNamespace(currentGitGc.getMetadata().getNamespace())
            .list()
            .getItems();
    gitGcs.remove(currentGitGc);
    GitGarbageCollectionStatus currentGitGcStatus = currentGitGc.getStatus();
    currentGitGcStatus.resetExcludedProjects();
    for (GitGarbageCollection gc : gitGcs) {
      currentGitGcStatus.excludeProjects(gc.getSpec().getProjects());
    }
    currentGitGc.setStatus(currentGitGcStatus);

    return currentGitGc;
  }

  private void validateGitGCProjectList(GitGarbageCollection gitGc) {
    List<GitGarbageCollection> gitGcs =
        kubernetesClient
            .resources(GitGarbageCollection.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .list()
            .getItems();
    Set<String> projects = gitGc.getSpec().getProjects();

    gitGcs.remove(gitGc);
    log.debug("Detected GitGcs: {}", gitGcs);
    List<GitGarbageCollection> allProjectGcs =
        gitGcs.stream().filter(gc -> gc.getStatus().isReplicateAll()).collect(Collectors.toList());
    if (!allProjectGcs.isEmpty() && projects.isEmpty()) {
      throw new GitGarbageCollectionConflictException(
          "Multiple Git GC jobs working on all projects are not allowed.");
    }

    Set<String> projectsWithExistingGC =
        gitGcs.stream()
            .map(gc -> gc.getSpec().getProjects())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    Set<String> projectsIntercept = getIntercept(projects, projectsWithExistingGC);
    if (projectsIntercept.isEmpty()) {
      return;
    }
    log.debug("Found conflicting projects: {}", projectsIntercept);

    if (gitGcs.stream()
        .filter(gc -> !getIntercept(projects, gc.getSpec().getProjects()).isEmpty())
        .allMatch(gc -> gc.getStatus().getState().equals(GitGcState.CONFLICT))) {
      log.debug("All other GitGcs are marked as conflicting. Activating {}", gitGc);
      return;
    }
    log.debug("{} will be marked as conflicting");
    throw new GitGarbageCollectionConflictException(projectsIntercept);
  }

  private Set<String> getIntercept(Set<String> set1, Set<String> set2) {
    Set<String> intercept = new HashSet<>();
    intercept.addAll(set1);
    intercept.retainAll(set2);
    return intercept;
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

  @Override
  public ErrorStatusUpdateControl<GitGarbageCollection> updateErrorStatus(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context, Exception e) {
    GitGarbageCollectionStatus status = new GitGarbageCollectionStatus();
    if (e instanceof GitGarbageCollectionConflictException) {
      status.setState(GitGcState.CONFLICT);
    } else {
      status.setState(GitGcState.ERROR);
    }
    gitGc.setStatus(status);

    return ErrorStatusUpdateControl.updateStatus(gitGc);
  }
}
