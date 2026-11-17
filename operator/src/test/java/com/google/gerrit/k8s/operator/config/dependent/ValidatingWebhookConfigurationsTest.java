// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.config.dependent;

import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.config.GerritOperatorConfigReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.MockKeyStoreProvider;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import org.junit.jupiter.api.Test;

public class ValidatingWebhookConfigurationsTest extends AbstractOperatorUnitTest {

  @Test
  public void webhooksAndServiceCreatedWhenEnabled() {
    GerritOperatorConfig config = TestFixtures.operatorConfig().load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(
            client -> new GerritOperatorConfigReconciler(new MockKeyStoreProvider()))) {
      mock.create(config);

      for (String resourceName : Constants.RESOURCES_WITH_VALIDATING_WEBHOOK) {
        String vwcName = resourceName.toLowerCase();
        ValidatingWebhookConfiguration actual =
            mock.awaitClusterResource(ValidatingWebhookConfiguration.class, vwcName);
        TestFixtures.validatingWebhookConfiguration(patchesFor(resourceName)).assertEqualTo(actual);
      }

      Service actualService =
          mock.awaitResource(
              Service.class,
              config.getMetadata().getNamespace(),
              GerritOperatorService.SERVICE_NAME);
      TestFixtures.fixture(Service.class, getClass(), TestFixtures.OPERATOR_SERVICE)
          .assertEqualTo(actualService);
    }
  }

  @Test
  public void noWebhooksOrServiceCreatedWhenDisabled() {
    GerritOperatorConfig config =
        TestFixtures.operatorConfig(TestFixtures.OPERATOR_CONFIG_WEBHOOK_DISABLED_PATCH).load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(
            client -> new GerritOperatorConfigReconciler(new MockKeyStoreProvider()))) {
      mock.create(config);

      for (String resourceName : Constants.RESOURCES_WITH_VALIDATING_WEBHOOK) {
        mock.awaitAbsent(ValidatingWebhookConfiguration.class, resourceName.toLowerCase());
      }
      mock.awaitAbsent(
          Service.class, config.getMetadata().getNamespace(), GerritOperatorService.SERVICE_NAME);
    }
  }

  private String[] patchesFor(String resourceName) {
    return switch (resourceName) {
      case Constants.GERRIT_KIND -> new String[] {TestFixtures.VWC_GERRITS_PATCH};
      case Constants.GERRIT_MAINTENANCE_KIND -> new String[] {TestFixtures.VWC_MAINTENANCES_PATCH};
      default -> new String[] {};
    };
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
