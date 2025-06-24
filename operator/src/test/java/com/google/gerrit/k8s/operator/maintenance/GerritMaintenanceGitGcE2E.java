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

package com.google.gerrit.k8s.operator.maintenance;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class GerritMaintenanceGitGcE2E extends AbstractGerritOperatorE2ETest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String GITGC_SCHEDULE = "*/1 * * * *";

  @Test
  void testGitGcAllProjectsCreationAndDeletion() {
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            operator.getNamespace(), List.of(Set.of()));
    client.resource(gm).createOrReplace();
    logger.atInfo().log("Waiting max 2 minutes for Git Gc Cronjob to be created.");
    String gcCronJobName = gm.getSpec().getProjects().getGc().get(0).getName();
    await()
        .atMost(20, MINUTES)
        .untilAsserted(
            () -> {
              assertGerritMaintenanceCreation(gm.getMetadata().getName());
              assertGitGcCronJobCreation(gcCronJobName);
            });

    logger.atInfo().log("Deleting test GitMaintenance object: %s", gm);
    client.resource(gm).delete();
    awaitGitGcDeletionAssertion(gm.getMetadata().getName(), gcCronJobName);
  }

  @Test
  void testGitGcSelectedProjects() {
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            operator.getNamespace(), List.of(Set.of("All-Projects", "test")));
    client.resource(gm).createOrReplace();
    logger.atInfo().log("Waiting max 2 minutes for GerritMaintenance to be created.");
    String gcCronJobName = gm.getSpec().getProjects().getGc().get(0).getName();
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGerritMaintenanceCreation(gm.getMetadata().getName());
              assertGitGcCronJobCreation(gcCronJobName);
            });

    client.resource(gm).delete();
  }

  @Test
  void testSelectiveGcIsExcludedFromCompleteGc() {
    Set<String> selectedProjects = Set.of("All-Projects", "test");
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            operator.getNamespace(), List.of(Set.of(), selectedProjects));
    String gmName = gm.getMetadata().getName();
    client.resource(gm).createOrReplace();
    logger.atInfo().log("Waiting max 2 minutes for GerritMaintenance to be created.");
    String completeGcCronJobName = gm.getSpec().getProjects().getGc().get(0).getName();
    String selectedGcCronJobName = gm.getSpec().getProjects().getGc().get(1).getName();
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGerritMaintenanceCreation(gmName);
              assertGitGcCronJobCreation(completeGcCronJobName);
              assertGitGcCronJobCreation(selectedGcCronJobName);
            });

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              CronJob updatedCompleteGitGc =
                  client
                      .resources(CronJob.class)
                      .inNamespace(operator.getNamespace())
                      .withName(completeGcCronJobName)
                      .get();
              assert updatedCompleteGitGc
                  .getSpec()
                  .getJobTemplate()
                  .getSpec()
                  .getTemplate()
                  .getSpec()
                  .getContainers()
                  .get(0)
                  .getArgs()
                  .containsAll(selectedProjects);
            });

    gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            operator.getNamespace(), List.of(Set.of()));
    client.resource(gm).createOrReplace();
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertNull(
                  client
                      .resources(CronJob.class)
                      .inNamespace(operator.getNamespace())
                      .withName(selectedGcCronJobName)
                      .get());
              CronJob updatedCompleteGitGc =
                  client
                      .resources(CronJob.class)
                      .inNamespace(operator.getNamespace())
                      .withName(completeGcCronJobName)
                      .get();
              assert updatedCompleteGitGc
                  .getSpec()
                  .getJobTemplate()
                  .getSpec()
                  .getTemplate()
                  .getSpec()
                  .getContainers()
                  .get(0)
                  .getArgs()
                  .isEmpty();
            });
  }

  private void assertGerritMaintenanceCreation(String gmName) {
    GerritMaintenance updatedGm =
        client
            .resources(GerritMaintenance.class)
            .inNamespace(operator.getNamespace())
            .withName(gmName)
            .get();
    assertThat(updatedGm, is(notNullValue()));
  }

  private void assertGitGcCronJobCreation(String gitGcName) {
    CronJob cronJob =
        client
            .batch()
            .v1()
            .cronjobs()
            .inNamespace(operator.getNamespace())
            .withName(gitGcName)
            .get();
    assertThat(cronJob, is(notNullValue()));
  }

  private void awaitGitGcDeletionAssertion(String gmName, String gitGcName) {
    logger.atInfo().log("Waiting max 2 minutes for GerritMaintenance to be deleted.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              GerritMaintenance updatedGm =
                  client
                      .resources(GerritMaintenance.class)
                      .inNamespace(operator.getNamespace())
                      .withName(gmName)
                      .get();
              assertNull(updatedGm);

              CronJob cronJob =
                  client
                      .batch()
                      .v1()
                      .cronjobs()
                      .inNamespace(operator.getNamespace())
                      .withName(gitGcName)
                      .get();
              assertNull(cronJob);
            });
  }

  @Override
  protected IngressType getIngressType() {
    return IngressType.NONE;
  }
}
