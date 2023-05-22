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

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.model.GerritTemplate;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;

@Singleton
public class GerritClusterAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;

  @Override
  Status validate(HasMetadata resource) {
    GerritCluster gerritCluster = (GerritCluster) resource;

    if (moreThanOnePrimaryGerritInCluster(gerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage("Only a single primary Gerrit is allowed per Gerrit Cluster.")
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

  private boolean moreThanOnePrimaryGerritInCluster(GerritCluster gerritCluster) {
    return gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode() == GerritMode.PRIMARY)
            .count()
        > 1;
  }

  @Override
  public String getName() {
    return "gerritcluster";
  }
}
