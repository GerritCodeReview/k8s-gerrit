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

package com.google.gerrit.k8s.operator;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitGarbageCollectionE2E {
  static final Logger log = LoggerFactory.getLogger(GitGarbageCollectionE2E.class);
  static final String GITGC_SCHEDULE = "*/1 * * * *";

  static final KubernetesClient client = getKubernetesClient();

  @RegisterExtension
  LocalOperatorExtension operator =
      LocalOperatorExtension.builder()
          .waitForNamespaceDeletion(false)
          .withReconciler(new GitGarbageCollectionReconciler(client))
          .build();

  @Test
  void testGitGcAllProjectsCreationAndDeletion() {
    GitGarbageCollection gitGc = createCompleteGc();

    log.info("Waiting max 2 minutes for GitGc to be created.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertGitGcCreation(gitGc.getMetadata().getName());
              assertGitGcCronJobCreation(gitGc.getMetadata().getName());
              assertGitGcJobCreation(gitGc.getMetadata().getName());
            });

    log.info("Deleting test GitGc object: {}", gitGc);
    client.resources(GitGarbageCollection.class).delete(gitGc);

    log.info("Waiting max 2 minutes for GitGc to be deleted.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              GitGarbageCollection updatedGitGc =
                  client
                      .resources(GitGarbageCollection.class)
                      .inNamespace(operator.getNamespace())
                      .withName(gitGc.getMetadata().getName())
                      .get();
              assertNull(updatedGitGc);

              CronJob cronJob =
                  client
                      .batch()
                      .v1()
                      .cronjobs()
                      .inNamespace(operator.getNamespace())
                      .withName(gitGc.getMetadata().getName())
                      .get();
              assertNull(cronJob);
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
    spec.setLogPVC("log-pvc");
    spec.setRepositoryPVC("repo-pvc");
    gitGc.setSpec(spec);

    log.info("Creating test GitGc object: {}", gitGc);
    client.resources(GitGarbageCollection.class).create(gitGc);
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

  private void assertGitGcJobCreation(String gitGcName) {
    List<Job> jobRuns =
        client.batch().v1().jobs().inNamespace(operator.getNamespace()).list().getItems();
    assert (jobRuns.size() > 0);
    assert (jobRuns.get(0).getMetadata().getName().startsWith(gitGcName));
  }

  private static KubernetesClient getKubernetesClient() {
    Config config;
    try {
      String kubeconfig = System.getenv("KUBECONFIG");
      if (kubeconfig != null) {
        config = Config.fromKubeconfig(Files.readString(Path.of(kubeconfig)));
        return new DefaultKubernetesClient(config);
      }
      log.warn("KUBECONFIG variable not set. Using default config.");
    } catch (IOException e) {
      log.error("Failed to load kubeconfig. Trying default", e);
    }
    return new DefaultKubernetesClient();
  }
}
