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

import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritMaintenanceTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedCronJobCreated(String inputFile, List<String> expectedOutputFiles) {
    GerritMaintenance input =
        ReconcilerUtils.loadYaml(GerritMaintenance.class, this.getClass(), inputFile);

    List<CronJob> expected = new ArrayList<>();
    for (String file : expectedOutputFiles) {
      expected.add(ReconcilerUtils.loadYaml(CronJob.class, this.getClass(), file));
    }
    Map<ResourceID, CronJob> cronJobs =
        new GerritMaintenanceCronJobs().desiredResources(input, null);

    for (CronJob expectedCronJob : expected) {
      assertThat(cronJobs.get(ResourceID.fromResource(expectedCronJob))).isEqualTo(expectedCronJob);
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../gerrit-maintenance.yaml",
            List.of("cronjob_gitgc_all.yaml", "cronjob_gitgc_selected.yaml")));
  }
}
