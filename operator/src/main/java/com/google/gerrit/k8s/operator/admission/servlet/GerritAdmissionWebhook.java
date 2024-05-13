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

package com.google.gerrit.k8s.operator.admission.servlet;

import static com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig.RefDatabase.SPANNER;
import static com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig.RefDatabase.ZOOKEEPER;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.shared.EventsBrokerConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.gerrit.k8s.operator.server.ValidatingAdmissionWebhookServlet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;

@Singleton
public class GerritAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;

  private final ClusterMode clusterMode;

  public static final String NO_REFDB_CONFIGURED_MSG =
      "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE";

  public static final String NO_EVENTS_BROKER_CONFIGURED_MSG =
      "An events-broker is required to run Gerrit in multisite mode: .spec.eventsBroker.brokerType != NONE";

  @Inject
  public GerritAdmissionWebhook(ClusterMode clusterMode) {
    this.clusterMode = clusterMode;
  }

  @Override
  public Status validate(HasMetadata resource) {
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

    if (noRefDbConfiguredForMultiPrimary(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(NO_REFDB_CONFIGURED_MSG)
          .build();
    }

    if (noEventsBrokerConfiguredForMultisite(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(NO_EVENTS_BROKER_CONFIGURED_MSG)
          .build();
    }

    if (missingRefdbConfig(gerrit)) {
      String refDbName = "";
      switch (gerrit.getSpec().getRefdb().getDatabase()) {
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

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  private void invalidGerritConfiguration(Gerrit gerrit) throws InvalidGerritConfigException {
    new GerritConfigBuilder(gerrit).validate();
  }

  private boolean noRefDbConfiguredForMultiPrimary(Gerrit gerrit) {
    return (gerrit.getSpec().isHighlyAvailablePrimary() || clusterMode == ClusterMode.MULTISITE)
        && gerrit.getSpec().getRefdb().getDatabase().equals(GlobalRefDbConfig.RefDatabase.NONE);
  }

  private boolean noEventsBrokerConfiguredForMultisite(Gerrit gerrit) {
    return clusterMode == ClusterMode.MULTISITE
        && gerrit
            .getSpec()
            .getEventsBroker()
            .getBrokerType()
            .equals(EventsBrokerConfig.BrokerType.NONE);
  }

  private boolean missingRefdbConfig(Gerrit gerrit) {
    GlobalRefDbConfig refDbConfig = gerrit.getSpec().getRefdb();
    switch (refDbConfig.getDatabase()) {
      case ZOOKEEPER:
        return refDbConfig.getZookeeper() == null;
      case SPANNER:
        return refDbConfig.getSpanner() == null;
      default:
        return false;
    }
  }

  @Override
  public String getName() {
    return "gerrit";
  }

  @Override
  public String getVersion() {
    return "v1beta1";
  }
}
