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

package com.google.gerrit.k8s.operator.server;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GitGcAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long serialVersionUID = 1L;
  private static final Status OK_STATUS =
      new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();

  private final KubernetesClient client;

  @Inject
  public GitGcAdmissionWebhook(KubernetesClient client) {
    this.client = client;
  }

  @Override
  Status validate(HasMetadata resource) {
    GitGarbageCollection gitGc = (GitGarbageCollection) resource;
    List<GitGarbageCollection> gitGcs =
        client
            .resources(GitGarbageCollection.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .list()
            .getItems()
            .stream()
            .filter(gc -> !gc.getMetadata().getUid().equals(gitGc.getMetadata().getUid()))
            .collect(Collectors.toList());
    Set<String> projects = gitGc.getSpec().getProjects();

    logger.atFine().log("Detected GitGcs: %s", gitGcs);
    if (projects.isEmpty()) {
      if (gitGcs.stream().anyMatch(gc -> gc.getSpec().getProjects().isEmpty())) {
        return new StatusBuilder()
            .withCode(HttpServletResponse.SC_CONFLICT)
            .withMessage("Only a single GitGc working on all projects allowed per GerritCluster.")
            .build();
      }
      return OK_STATUS;
    }

    Set<String> projectsWithExistingGC =
        gitGcs.stream()
            .map(gc -> gc.getSpec().getProjects())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    Set<String> projectsIntercept = getIntercept(projects, projectsWithExistingGC);
    if (projectsIntercept.isEmpty()) {
      return OK_STATUS;
    }
    return new StatusBuilder()
        .withCode(HttpServletResponse.SC_CONFLICT)
        .withMessage(
            "Only a single GitGc is allowed to work on a given project. Conflict for projects: "
                + projectsIntercept)
        .build();
  }

  private Set<String> getIntercept(Set<String> set1, Set<String> set2) {
    Set<String> intercept = new HashSet<>();
    intercept.addAll(set1);
    intercept.retainAll(set2);
    return intercept;
  }

  @Override
  public String getName() {
    return "gitgc";
  }
}
