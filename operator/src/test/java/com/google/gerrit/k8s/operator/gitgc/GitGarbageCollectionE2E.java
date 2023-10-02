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

package com.google.gerrit.k8s.operator.gitgc;

import static com.google.gerrit.k8s.operator.test.TestGerritCluster.CLUSTER_NAME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gitgc.GitGarbageCollectionSpec;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gitgc.GitGarbageCollectionStatus;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class GitGarbageCollectionE2E extends AbstractGerritOperatorE2ETest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String GITGC_SCHEDULE = "*/1 * * * *";

  @Test
  void testGitGcAllProjectsCreationAndDeletion() {
    GitGarbageCollection gitGc = createCompleteGc();

    logger.atInfo().log("Waiting max 2 minutes for GitGc to be created.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGitGcCreation(gitGc.getMetadata().getName());
              assertGitGcCronJobCreation(gitGc.getMetadata().getName());
              assertGitGcJobCreation(gitGc.getMetadata().getName());
            });

    logger.atInfo().log("Deleting test GitGc object: %s", gitGc);
    client.resource(gitGc).delete();
    awaitGitGcDeletionAssertion(gitGc.getMetadata().getName());
  }

  @Test
  void testGitGcSelectedProjects() {
    GitGarbageCollection gitGc = createSelectiveGc("selective-gc", Set.of("All-Projects", "test"));

    logger.atInfo().log("Waiting max 2 minutes for GitGc to be created.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGitGcCreation(gitGc.getMetadata().getName());
              assertGitGcCronJobCreation(gitGc.getMetadata().getName());
              assertGitGcJobCreation(gitGc.getMetadata().getName());
            });

    client.resource(gitGc).delete();
  }

  @Test
  void testSelectiveGcIsExcludedFromCompleteGc() {
    GitGarbageCollection completeGitGc = createCompleteGc();

    logger.atInfo().log("Waiting max 2 minutes for GitGc to be created.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGitGcCreation(completeGitGc.getMetadata().getName());
              assertGitGcCronJobCreation(completeGitGc.getMetadata().getName());
            });

    Set<String> selectedProjects = Set.of("All-Projects", "test");
    GitGarbageCollection selectiveGitGc = createSelectiveGc("selective-gc", selectedProjects);

    logger.atInfo().log("Waiting max 2 minutes for GitGc to be created.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGitGcCreation(selectiveGitGc.getMetadata().getName());
              assertGitGcCronJobCreation(selectiveGitGc.getMetadata().getName());
            });

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              GitGarbageCollection updatedCompleteGitGc =
                  client
                      .resources(GitGarbageCollection.class)
                      .inNamespace(operator.getNamespace())
                      .withName(completeGitGc.getMetadata().getName())
                      .get();
              assert updatedCompleteGitGc
                  .getStatus()
                  .getExcludedProjects()
                  .containsAll(selectedProjects);
            });

    client.resource(selectiveGitGc).delete();
    awaitGitGcDeletionAssertion(selectiveGitGc.getMetadata().getName());

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              GitGarbageCollection updatedCompleteGitGc =
                  client
                      .resources(GitGarbageCollection.class)
                      .inNamespace(operator.getNamespace())
                      .withName(completeGitGc.getMetadata().getName())
                      .get();
              assert updatedCompleteGitGc.getStatus().getExcludedProjects().isEmpty();
            });
  }

  private GitGarbageCollection createCompleteGc() {
    GitGarbageCollection gitGc = new GitGarbageCollection();
    gitGc.setMetadata(
        new ObjectMetaBuilder()
            .withName("gitgc-complete")
            .withNamespace(operator.getNamespace())
            .build());
    GitGarbageCollectionSpec spec = new GitGarbageCollectionSpec();
    spec.setSchedule(GITGC_SCHEDULE);
    spec.setCluster(CLUSTER_NAME);
    gitGc.setSpec(spec);

    logger.atInfo().log("Creating test GitGc object: %s", gitGc);
    client.resource(gitGc).createOrReplace();

    return gitGc;
  }

  private GitGarbageCollection createSelectiveGc(String name, Set<String> projects) {
    GitGarbageCollection gitGc = new GitGarbageCollection();
    gitGc.setMetadata(
        new ObjectMetaBuilder().withName(name).withNamespace(operator.getNamespace()).build());
    GitGarbageCollectionSpec spec = new GitGarbageCollectionSpec();
    spec.setSchedule(GITGC_SCHEDULE);
    spec.setCluster(CLUSTER_NAME);
    spec.setProjects(projects);
    gitGc.setSpec(spec);

    logger.atInfo().log("Creating test GitGc object: %s", gitGc);
    client.resource(gitGc).createOrReplace();

    return gitGc;
  }

  private void assertGitGcCreation(String gitGcName) {
    GitGarbageCollection updatedGitGc =
        client
            .resources(GitGarbageCollection.class)
            .inNamespace(operator.getNamespace())
            .withName(gitGcName)
            .get();
    assertThat(updatedGitGc, is(notNullValue()));
    assertThat(
        updatedGitGc.getStatus().getState(),
        is(not(equalTo(GitGarbageCollectionStatus.GitGcState.ERROR))));
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

  private void awaitGitGcDeletionAssertion(String gitGcName) {
    logger.atInfo().log("Waiting max 2 minutes for GitGc to be deleted.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              GitGarbageCollection updatedGitGc =
                  client
                      .resources(GitGarbageCollection.class)
                      .inNamespace(operator.getNamespace())
                      .withName(gitGcName)
                      .get();
              assertNull(updatedGitGc);

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

  private void assertGitGcJobCreation(String gitGcName) {
    List<Job> jobRuns =
        client.batch().v1().jobs().inNamespace(operator.getNamespace()).list().getItems();
    assert (jobRuns.size() > 0);
    assert (jobRuns.get(0).getMetadata().getName().startsWith(gitGcName));
  }

  @Override
  protected IngressType getIngressType() {
    return IngressType.NONE;
  }
}
