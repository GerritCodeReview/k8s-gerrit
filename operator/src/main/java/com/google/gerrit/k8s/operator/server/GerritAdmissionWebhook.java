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

import static com.google.gerrit.k8s.operator.gerrit.config.SpannerRefDbPluginConfigBuilder.SPANNER_CREDENTIALS_PATH;
import static com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig.RefDatabase.SPANNER;
import static com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig.RefDatabase.ZOOKEEPER;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritSecret;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map.Entry;

@Singleton
public class GerritAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long serialVersionUID = 1L;

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
              "A Ref-Database is required to horizontally scale a primary Gerrit:"
                  + " .spec.refdb.database != NONE")
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

    if (refDbConfig.getDatabase().equals(SPANNER)) {
      if (missingSpannerRefdbCredentialsFile(gerrit)) {
        return new StatusBuilder()
            .withCode(HttpServletResponse.SC_BAD_REQUEST)
            .withMessage(
                String.format("Missing spanner credentials in %s", SPANNER_CREDENTIALS_PATH))
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

  private boolean missingSpannerRefdbCredentialsFile(Gerrit gerrit) {
    GerritSecret spannerRefdbCredentialsSecret = new GerritSecret();
    // TODO: Instead of making GerritSecret() .getSecret(), should just inject the
    // KubernetesDependentResource for api access
    for (Entry<String, String> entry :
        spannerRefdbCredentialsSecret
            .getSecretMap(gerrit.getMetadata().getNamespace(), gerrit.getSpec().getSecretRef())
            .entrySet()) {
      logger.atSevere().log(entry.getKey() + entry.getValue());
      // TODO: Check key for SPANNER_CREDENTIALS_PATH
    }
    return false;
  }

  @Override
  public String getName() {
    return "gerrit";
  }
}
