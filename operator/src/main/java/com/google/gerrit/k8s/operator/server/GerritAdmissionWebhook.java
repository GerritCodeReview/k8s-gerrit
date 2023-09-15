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

import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.shared.model.GlobalRefDbConfig;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;

@Singleton
public class GerritAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
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
              "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE")
          .build();
    }

    if (missingZookeeperConfig(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Missing zookeeper configuration (.spec.refdb.zookeeper).")
          .build();
    }

    if (missingSpannerConfig(gerrit)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Missing spanner configuration (.spec.refdb.spanner).")
          .build();
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

  private boolean missingRefdbConfig(Gerrit gerrit) {
    switch (gerrit.getSpec().getRefdb().getDatabase()) {
      case GlobalRefDbConfig.RefDatabase.ZOOKEEPER:
        return gerrit.getSpec().getRefdb().getZookeeper() == null;
      case GlobalRefDbConfig.RefDatabase.SPANNER:
        return gerrit.getSpec().getRefdb().getSpanner() == null;
      default:
        return true;
    }
  }

  @Override
  public String getName() {
    return "gerrit";
  }
}
