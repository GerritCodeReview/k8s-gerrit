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
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration
@Deprecated
public class GitGarbageCollectionReconciler implements Reconciler<GitGarbageCollection> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final KubernetesClient client;

  private GitGarbageCollectionCronJob dependentCronJob;

  @Inject
  public GitGarbageCollectionReconciler(KubernetesClient client) {
    this.client = client;
    this.dependentCronJob = new GitGarbageCollectionCronJob();
  }

  @Override
  public List<EventSource<?, GitGarbageCollection>> prepareEventSources(
      EventSourceContext<GitGarbageCollection> context) {
    List<EventSource<?, GitGarbageCollection>> eventSources = new ArrayList<>();
    eventSources.addAll(EventSourceUtils.dependentEventSources(context, dependentCronJob));

    final SecondaryToPrimaryMapper<GitGarbageCollection> specificProjectGitGcMapper =
        (GitGarbageCollection gc) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getProjects().isEmpty())
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GitGarbageCollection, GitGarbageCollection> gitGcEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    GitGarbageCollection.class, GitGarbageCollection.class)
                .withSecondaryToPrimaryMapper(specificProjectGitGcMapper)
                .build(),
            context);
    eventSources.add(gitGcEventSource);

    final SecondaryToPrimaryMapper<GerritCluster> gerritClusterMapper =
        (GerritCluster cluster) ->
            context
                .getPrimaryCache()
                .list(gitGc -> gitGc.getSpec().getCluster().equals(cluster.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritCluster, GitGarbageCollection> gerritClusterEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(GerritCluster.class, GitGarbageCollection.class)
                .withSecondaryToPrimaryMapper(gerritClusterMapper)
                .build(),
            context);
    eventSources.add(gerritClusterEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GitGarbageCollection> reconcile(
      GitGarbageCollection gitGc, Context<GitGarbageCollection> context) {
    if (gitGc.getSpec().getProjects().isEmpty()) {
      gitGc = excludeProjectsHandledSeparately(gitGc);
    }

    dependentCronJob.reconcile(gitGc, context);
    return UpdateControl.patchStatus(updateGitGcStatus(gitGc));
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

    return ErrorStatusUpdateControl.patchStatus(gitGc);
  }
}
