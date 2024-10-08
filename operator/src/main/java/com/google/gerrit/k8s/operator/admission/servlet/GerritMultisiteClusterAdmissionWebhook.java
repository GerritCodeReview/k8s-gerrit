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

package com.google.gerrit.k8s.operator.admission.servlet;

import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.server.ValidatingAdmissionWebhookServlet;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Singleton
public class GerritMultisiteClusterAdmissionWebhook extends ValidatingAdmissionWebhookServlet {

  public static final String GERRIT_MULTISITE_MISCONFIGURED =
      "Gerrit Cluster in multisite mode should be configured as Primary Gerrit and have spec.gerrits[0].specs.replicas value > 1.";

  public GerritMultisiteClusterAdmissionWebhook() {} // TODO remove?

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

    if (isMultisiteMisconfigured(gerritCluster)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage(GERRIT_MULTISITE_MISCONFIGURED)
          .build();
    }

    GerritMultisiteAdmissionWebhook gerritAdmission = new GerritMultisiteAdmissionWebhook();
    for (GerritTemplate gerrit : gerritCluster.getSpec().getGerrits()) {
      Status status = gerritAdmission.validate(gerrit.toGerrit(gerritCluster));
      if (status.getCode() != HttpServletResponse.SC_OK) {
        return status;
      }
    }

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  private boolean isMultisiteMisconfigured(GerritCluster gerritCluster) {
    List<GerritTemplate> gerrits = gerritCluster.getSpec().getGerrits();
    return !(gerrits.size() == 1
        && gerrits.get(0).getSpec().getMode() == GerritMode.PRIMARY
        && gerrits.get(0).getSpec().getReplicas() > 1);
  }

  @Override
  public String getName() {
    return Constants.GERRIT_CLUSTER_KIND;
  }
}
