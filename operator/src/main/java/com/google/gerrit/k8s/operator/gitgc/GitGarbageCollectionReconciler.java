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
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollectionStatus;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollectionStatus.GitGcState;
import com.google.gerrit.k8s.operator.gitgc.dependent.GitGarbageCollectionCronJob;
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
import java.util.List;
import java.util.Map;
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
        gitGcEventSource, gerritClusterEventSource, dependentCronJob.initEventSource(context));
  }

  @Override
  public UpdateControl<GitGarbageCollection> reconcile(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    if (gitGc.getSpec().getProjects().isEmpty()) {
      gitGc = excludeProjectsHandledSeparately(gitGc);
    }

    dependentCronJob.reconcile(gitGc, context);
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
