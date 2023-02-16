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

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec.GerritMode;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletResponse;

@Singleton
public class GerritAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;

  private final KubernetesClient client;

  @Inject
  public GerritAdmissionWebhook(KubernetesClient client) {
    this.client = client;
  }

  @Override
  Status validate(HasMetadata resource) {
    Gerrit gerrit = (Gerrit) resource;

    if (noClusterNameSet(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Gerrit.spec.cluster is required.")
          .build();
    }

    if (moreThanOnePrimaryGerritInCluster(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage("Only a single primary Gerrit is allowed per Gerrit Cluster.")
          .build();
    }

    try {
      invalidGerritConfiguration(gerrit);
    } catch (InvalidGerritConfigException e) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(e.getMessage())
          .build();
    }

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  private boolean noClusterNameSet(Gerrit gerrit) {
    String clusterName = gerrit.getSpec().getCluster();
    return clusterName == null || clusterName.isBlank();
  }

  private boolean moreThanOnePrimaryGerritInCluster(Gerrit gerrit) {
    if (gerrit.getSpec().getMode() != GerritMode.PRIMARY) {
      return false;
    }
    String clusterName = gerrit.getSpec().getCluster();
    return client
        .resources(Gerrit.class)
        .inNamespace(gerrit.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .anyMatch(
            g ->
                clusterName.equals(g.getSpec().getCluster())
                    && g.getSpec().getMode() == GerritMode.PRIMARY
                    && !gerrit.getMetadata().getName().equals(g.getMetadata().getName()));
  }

  private void invalidGerritConfiguration(Gerrit gerrit) throws InvalidGerritConfigException {
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(gerrit.getSpec().getCluster())
            .get();
    new GerritConfigBuilder().forGerrit(gerrit, gerritCluster).validate();
  }

  @Override
  public String getName() {
    return "gerrit";
  }
}
