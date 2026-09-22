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
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_CRONJOB_GC_SELECTED_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_CRONJOB_GC_SELECTED_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_GC_SELECTED_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.MAINTENANCE_GC_SELECTED_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritMaintenanceTest {

  @BeforeAll
  public void setup() {
    OperatorContext.createInstance(ClusterMode.HIGH_AVAILABILITY, "cluster.local");
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedCronJobCreated(
      KubernetesResourceFixture<GerritMaintenance> inputFixture,
      List<KubernetesResourceFixture<CronJob>> expectedCronJobFixtures) {
    GerritMaintenance input = inputFixture.load();

    Map<String, CronJob> cronJobs = new GerritMaintenanceCronJobs().desiredResources(input, null);

    assertThat(cronJobs).hasSize(expectedCronJobFixtures.size());
    for (KubernetesResourceFixture<CronJob> fixture : expectedCronJobFixtures) {
      CronJob expected = fixture.load();
      assertUnordered(cronJobs.get(expected.getMetadata().getName()), expected);
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
}
