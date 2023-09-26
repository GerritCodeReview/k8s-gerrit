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

import static com.google.gerrit.k8s.operator.gerrit.config.SpannerRefDbPluginConfigBuilder.SPANNER_CREDENTIALS_FILE;
import static com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig.RefDatabase.SPANNER;
import static com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig.RefDatabase.ZOOKEEPER;

import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;

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
    if (!(resource instanceof Gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Invalid resource. Expected Gerrit-resource for validation.")
          .build();
    }

    Gerrit gerrit = (Gerrit) resource;

    try {
      invalidGerritConfiguration(gerrit);
    } catch (InvalidGerritConfigException e) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(e.getMessage())
          .build();
    }

    if (noRefDbConfiguredForHA(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(
              "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE")
          .build();
    }

    GlobalRefDbConfig refDbConfig = gerrit.getSpec().getRefdb();

    if (missingRefdbConfig(refDbConfig)) {
      String refDbName = "";
      switch (refDbConfig.getDatabase()) {
        case ZOOKEEPER:
          refDbName = ZOOKEEPER.toString().toLowerCase(Locale.US);
          break;
        case SPANNER:
          refDbName = SPANNER.toString().toLowerCase(Locale.US);
          break;
        default:
          break;
      }
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(
              String.format("Missing %s configuration (.spec.refdb.%s)", refDbName, refDbName))
          .build();
    }

    String secretRef = gerrit.getSpec().getSecretRef();
    Secret secret = null;
    if (secretRef != null && !secretRef.isBlank()) {
      secret =
          client
              .secrets()
              .inNamespace(gerrit.getMetadata().getNamespace())
              .withName(secretRef)
              .get();

      if (secret == null) {
        return new StatusBuilder()
            .withCode(HttpServletResponse.SC_BAD_REQUEST)
            .withMessage(
                String.format("Secret %s in spec but not found (.spec.secretRef)", secretRef))
            .build();
      }
    }

    if (refDbConfig.getDatabase() == SPANNER) {
      if (secret == null) {
        return new StatusBuilder()
            .withCode(HttpServletResponse.SC_BAD_REQUEST)
            .withMessage(
                String.format(
                    "A secretRef with the name of a secret containing gcp-credentials.json is required to configure spanner credentials (.spec.secretRef)"))
            .build();
      }
      if (missingSpannerRefdbCredentialsFile(gerrit, secret)) {
        return new StatusBuilder()
            .withCode(HttpServletResponse.SC_BAD_REQUEST)
            .withMessage(
                String.format(
                    "Missing spanner credentials %s in secret %s",
                    SPANNER_CREDENTIALS_FILE, secretRef))
            .build();
      }
    }

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  private void invalidGerritConfiguration(Gerrit gerrit) throws InvalidGerritConfigException {
    new GerritConfigBuilder().forGerrit(gerrit).validate();
  }

  private boolean noRefDbConfiguredForHA(Gerrit gerrit) {
    return gerrit.getSpec().isHighlyAvailablePrimary()
        && gerrit.getSpec().getRefdb().getDatabase().equals(GlobalRefDbConfig.RefDatabase.NONE);
  }

  private boolean missingRefdbConfig(GlobalRefDbConfig refDbConfig) {
    switch (refDbConfig.getDatabase()) {
      case ZOOKEEPER:
        return refDbConfig.getZookeeper() == null;
      case SPANNER:
        return refDbConfig.getSpanner() == null;
      default:
        return false;
    }
  }

  private boolean missingSpannerRefdbCredentialsFile(Gerrit gerrit, Secret secret) {
    if (secret.getData().containsKey(SPANNER_CREDENTIALS_FILE)) {
      return false;
    }
    return true;
  }

  @Override
  public String getName() {
    return "gerrit";
  }
}
