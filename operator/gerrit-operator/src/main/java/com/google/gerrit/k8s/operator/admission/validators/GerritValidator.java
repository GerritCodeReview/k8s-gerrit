// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.admission.validators;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig.RefDatabase;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.InvalidGerritConfigException;
import com.google.inject.Singleton;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.validation.Validator;

@Singleton
public class GerritValidator implements Validator<Gerrit> {
  private static final String REFDB_CFG_MISSING_MSG_TEMPLATE =
      "Missing %s configuration (.spec.refdb.%s)";

  @Override
  public void validate(Gerrit gerrit, Operation operation) {
    try {
      invalidGerritConfiguration(gerrit);
    } catch (InvalidGerritConfigException e) {
      throw new NotAllowedException(
          "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE");
    }

    if (noRefDbConfiguredForHA(gerrit)) {
      throw new NotAllowedException(
          "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE");
    }

    if (missingRefdbConfig(gerrit)) {
      RefDatabase refDb = gerrit.getSpec().getRefdb().getDatabase();
      throw new NotAllowedException(String.format(REFDB_CFG_MISSING_MSG_TEMPLATE, refDb, refDb));
    }
  }

  private void invalidGerritConfiguration(Gerrit gerrit) throws InvalidGerritConfigException {
    new GerritConfigBuilder(gerrit).validate();
  }

  private boolean noRefDbConfiguredForHA(Gerrit gerrit) {
    return gerrit.getSpec().isHighlyAvailablePrimary()
        && gerrit.getSpec().getRefdb().getDatabase().equals(GlobalRefDbConfig.RefDatabase.NONE);
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
}
