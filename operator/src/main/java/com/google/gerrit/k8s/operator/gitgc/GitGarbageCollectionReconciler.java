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
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionStatus.GitGcState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration
public class GitGarbageCollectionReconciler
    implements Reconciler<GitGarbageCollection>,
        EventSourceInitializer<GitGarbageCollection>,
        ErrorStatusHandler<GitGarbageCollection> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final KubernetesClient client;

  private GitGarbageCollectionCronJob dependentCronJob;

  @Inject
  public GitGarbageCollectionReconciler(KubernetesClient client) {
    this.client = client;
    this.dependentCronJob = new GitGarbageCollectionCronJob();
    this.dependentCronJob.setKubernetesClient(client);
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

    final SecondaryToPrimaryMapper<GerritCluster> gerritClusterMapper =
        (GerritCluster cluster) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getCluster().equals(cluster.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritCluster, GitGarbageCollection> gerritClusterEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritCluster.class, context)
                .withSecondaryToPrimaryMapper(gerritClusterMapper)
                .build(),
            context);

    return EventSourceInitializer.nameEventSources(
        gitGcEventSource, gerritClusterEventSource, this.dependentCronJob.initEventSource(context));
  }

  @Override
  public UpdateControl<GitGarbageCollection> reconcile(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    validateGitGCProjectList(gitGc);
    if (gitGc.getSpec().getProjects().isEmpty()) {
      gitGc = excludeProjectsHandledSeparately(gitGc);
    }

    this.dependentCronJob.reconcile(gitGc, context);
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

  private GitGarbageCollection excludeProjectsHandledSeparately(GitGarbageCollection currentGitGc) {
    List<GitGarbageCollection> gitGcs =
        client
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
        client
            .resources(GitGarbageCollection.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .list()
            .getItems();
    Set<String> projects = gitGc.getSpec().getProjects();

    gitGcs =
        gitGcs.stream()
            .filter(gc -> !gc.getMetadata().getUid().equals(gitGc.getMetadata().getUid()))
            .collect(Collectors.toList());
    logger.atFine().log("Detected GitGcs: %s", gitGcs);
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
    logger.atFine().log("Found conflicting projects: %s", projectsIntercept);

    if (gitGcs.stream()
        .filter(gc -> !getIntercept(projects, gc.getSpec().getProjects()).isEmpty())
        .allMatch(gc -> gc.getStatus().getState().equals(GitGcState.CONFLICT))) {
      logger.atFine().log("All other GitGcs are marked as conflicting. Activating %s", gitGc);
      return;
    }
    logger.atFine().log("%s will be marked as conflicting", gitGc);
    throw new GitGarbageCollectionConflictException(projectsIntercept);
  }

  private Set<String> getIntercept(Set<String> set1, Set<String> set2) {
    Set<String> intercept = new HashSet<>();
    intercept.addAll(set1);
    intercept.retainAll(set2);
    return intercept;
  }

  @Override
  public ErrorStatusUpdateControl<GitGarbageCollection> updateErrorStatus(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context, Exception e) {
    GitGarbageCollectionStatus status = new GitGarbageCollectionStatus();
    if (e instanceof GitGarbageCollectionConflictException) {
      status.setState(GitGcState.CONFLICT);
    } else {
      logger.atSevere().withCause(e).log("Failed reconcile with message: %s", e.getMessage());
      status.setState(GitGcState.ERROR);
    }
    gitGc.setStatus(status);

    return ErrorStatusUpdateControl.updateStatus(gitGc);
  }
}
