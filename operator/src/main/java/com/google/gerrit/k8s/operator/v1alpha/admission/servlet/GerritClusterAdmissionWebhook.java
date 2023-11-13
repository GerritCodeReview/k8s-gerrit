// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.v1alpha.admission.servlet;

import com.google.gerrit.k8s.operator.server.ValidatingAdmissionWebhookServlet;
import com.google.gerrit.k8s.operator.v1alpha.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;

@Singleton
public class GerritClusterAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;

  @Override
  public Status validate(HasMetadata resource) {
    if (!(resource instanceof GerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Invalid resource. Expected GerritCluster-resource for validation.")
          .build();
    }

    GerritCluster gerritCluster = (GerritCluster) resource;

    if (multiplePrimaryGerritInCluster(gerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage("Only a single primary Gerrit is allowed per Gerrit Cluster.")
          .build();
    }

    if (primaryGerritAndReceiverInCluster(gerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage("A primary Gerrit cannot be in the same Gerrit Cluster as a Receiver.")
          .build();
    }

    if (multipleGerritReplicaInCluster(gerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage("Only a single Gerrit Replica is allowed per Gerrit Cluster.")
          .build();
    }

    GerritAdmissionWebhook gerritAdmission = new GerritAdmissionWebhook();
    for (GerritTemplate gerrit : gerritCluster.getSpec().getGerrits()) {
      Status status = gerritAdmission.validate(gerrit.toGerrit(gerritCluster));
      if (status.getCode() != HttpServletResponse.SC_OK) {
        return status;
      }
    }

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  private boolean multiplePrimaryGerritInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode() == GerritMode.PRIMARY)
            .count()
        > 1;
  }

  private boolean primaryGerritAndReceiverInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .anyMatch(g -> g.getSpec().getMode() == GerritMode.PRIMARY)
        && gerritCluster.getSpec().getReceiver() != null;
  }

  private boolean multipleGerritReplicaInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode() == GerritMode.REPLICA)
            .count()
        > 1;
  }

  @Override
  public String getName() {
    return "gerritcluster";
  }

  @Override
  public String getVersion() {
    return "v1alpha";
  }
}
