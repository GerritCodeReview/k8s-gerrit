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

package com.google.gerrit.k8s.operator.admission.validators;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.validation.Validator;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GitGcValidator implements Validator<GitGarbageCollection> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KubernetesClient client;

  @Inject
  public GitGcValidator(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void validate(GitGarbageCollection gitGc, Operation operation) {
    List<GitGarbageCollection> otherGitGcs = getOtherGitGcs(gitGc);
    Set<String> projects = gitGc.getSpec().getProjects();

    logger.atFine().log("Detected GitGcs: %s", otherGitGcs);
    if (multipleDefaultGitGcs(projects, otherGitGcs)) {
      throw new NotAllowedException(
          "Only a single GitGc working on all projects allowed per GerritCluster.", 409);
    }
    Set<String> projectsIntersection = gitGcProjectIntersect(projects, otherGitGcs);
    if (!projectsIntersection.isEmpty()) {
      throw new NotAllowedException(
          "Only a single GitGc is allowed to work on a given project. Conflict for projects: "
              + projectsIntersection,
          409);
    }
  }

  private List<GitGarbageCollection> getOtherGitGcs(GitGarbageCollection gitGc) {
    String gitGcUid = gitGc.getMetadata().getUid();
    return client
        .resources(GitGarbageCollection.class)
        .inNamespace(gitGc.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(gc -> !gc.getMetadata().getUid().equals(gitGcUid))
        .collect(Collectors.toList());
  }

  private boolean multipleDefaultGitGcs(
      Set<String> projects, List<GitGarbageCollection> otherGitGcs) {
    return projects.isEmpty()
        && otherGitGcs.stream().anyMatch(gc -> gc.getSpec().getProjects().isEmpty());
  }

  private Set<String> gitGcProjectIntersect(
      Set<String> projects, List<GitGarbageCollection> otherGitGcs) {
    Set<String> projectsWithExistingGC =
        otherGitGcs.stream()
            .map(gc -> gc.getSpec().getProjects())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    return getIntersection(projects, projectsWithExistingGC);
  }

  private Set<String> getIntersection(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>();
    intersection.addAll(set1);
    intersection.retainAll(set2);
    return intersection;
  }
}
