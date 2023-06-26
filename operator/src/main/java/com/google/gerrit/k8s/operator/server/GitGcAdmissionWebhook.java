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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GitGcAdmissionWebhook extends AdmissionWebhookServlet {
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
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    AdmissionReview admissionReq =
        objectMapper.readValue(request.getInputStream(), AdmissionReview.class);

    logger.atFine().log("GitGc admission request received: %s", admissionReq.toString());

    response.setContentType("application/json");
    AdmissionResponseBuilder admissionRespBuilder =
        new AdmissionResponseBuilder().withUid(admissionReq.getRequest().getUid());
    Status validationStatus =
        validateGitGCProjectList((GitGarbageCollection) admissionReq.getRequest().getObject());
    response.setStatus(HttpServletResponse.SC_OK);
    if (validationStatus.getCode() < 400) {
      admissionRespBuilder = admissionRespBuilder.withAllowed(true);
    } else {
      admissionRespBuilder = admissionRespBuilder.withAllowed(false).withStatus(validationStatus);
    }
    admissionReq.setResponse(admissionRespBuilder.build());
    objectMapper.writeValue(response.getWriter(), admissionReq);
    logger.atFine().log(
        "GitGc admission request responded with %s", admissionReq.getResponse().toString());
  }

  private Status validateGitGCProjectList(GitGarbageCollection gitGc) {
    String gitGcUid = gitGc.getMetadata().getUid();
    List<GitGarbageCollection> gitGcs =
        client
            .resources(GitGarbageCollection.class)
            .inNamespace(gitGc.getMetadata().getNamespace())
            .list()
            .getItems()
            .stream()
            .filter(gc -> !gc.getMetadata().getUid().equals(gitGcUid))
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
    Set<String> projectsIntersection = getIntersection(projects, projectsWithExistingGC);
    if (projectsIntersection.isEmpty()) {
      return OK_STATUS;
    }
    return new StatusBuilder()
        .withCode(HttpServletResponse.SC_CONFLICT)
        .withMessage(
            "Only a single GitGc is allowed to work on a given project. Conflict for projects: "
                + projectsIntersection)
        .build();
  }

  private Set<String> getIntersection(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>();
    intersection.addAll(set1);
    intersection.retainAll(set2);
    return intersection;
  }

  @Override
  public String getName() {
    return "gitgc";
  }
}
