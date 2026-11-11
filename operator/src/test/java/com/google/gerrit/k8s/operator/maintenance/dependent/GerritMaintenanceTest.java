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

package com.google.gerrit.k8s.operator.maintenance.dependent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_CRONJOB_GC_SELECTED_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_CRONJOB_GC_SELECTED_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_GC_SELECTED_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_GC_SELECTED_PATCH;
import static org.awaitility.Awaitility.await;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.maintenance.GerritMaintenanceReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritMaintenanceTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedCronJobCreated(
      KubernetesResourceFixture<GerritMaintenance> inputFixture,
      List<KubernetesResourceFixture<CronJob>> expectedCronJobFixtures) {
    GerritMaintenance input = inputFixture.load();
    String ns = input.getMetadata().getNamespace();
    Map<String, KubernetesResourceFixture<CronJob>> fixturesByName =
        expectedCronJobFixtures.stream()
            .collect(Collectors.toMap(KubernetesResourceFixture::getName, f -> f));
    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GerritMaintenanceReconciler())) {
      mock.create(input);
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                List<CronJob> actual =
                    mock.getClient().batch().v1().cronjobs().inNamespace(ns).list().getItems();
                assertThat(actual).hasSize(fixturesByName.size());
                for (CronJob actualCronJob : actual) {
                  String name = actualCronJob.getMetadata().getName();
                  assertThat(fixturesByName).containsKey(name);
                  fixturesByName.get(name).assertEqualTo(actualCronJob);
                }
              });
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> context = GerritMaintenanceTest.class;
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritMaintenance(context, MAINTENANCE_GC_SELECTED_PATCH),
            List.of(
                TestFixtures.maintenanceCronJob(),
                TestFixtures.maintenanceCronJob(MAINTENANCE_CRONJOB_GC_SELECTED_PATCH))),
        Arguments.of(
            TestFixtures.gerritMaintenance(
                context, MAINTENANCE_GC_SELECTED_PATCH, MAINTENANCE_GC_SELECTED_OPTIONS_PATCH),
            List.of(
                TestFixtures.maintenanceCronJob(),
                TestFixtures.maintenanceCronJob(
                    MAINTENANCE_CRONJOB_GC_SELECTED_PATCH,
                    MAINTENANCE_CRONJOB_GC_SELECTED_OPTIONS_PATCH))));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
