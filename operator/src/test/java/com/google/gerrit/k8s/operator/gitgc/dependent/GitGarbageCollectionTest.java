// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.gitgc.dependent;

import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CLUSTER_NFS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_CRONJOB_ARGS_OPTIONS_APPEND_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_CRONJOB_ARGS_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_CRONJOB_ARGS_SELECTED_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_CRONJOB_NFS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GITGC_SELECTED_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GitGarbageCollectionTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedCronJobCreated(
      KubernetesResourceFixture<GitGarbageCollection> inputFixture,
      KubernetesResourceFixture<GerritCluster> gerritClusterFixture,
      KubernetesResourceFixture<CronJob> expectedCronJobFixture) {
    GitGarbageCollection input = inputFixture.load();
    GerritCluster gerritCluster = gerritClusterFixture.load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GitGarbageCollectionReconciler(client))) {
      mock.create(gerritCluster);
      mock.create(input);
      expectedCronJobFixture.assertEqualTo(
          mock.awaitResource(
              CronJob.class, input.getMetadata().getNamespace(), input.getMetadata().getName()));
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> context = GitGarbageCollectionTest.class;
    return Stream.of(
        Arguments.of(
            TestFixtures.gitGarbageCollection(),
            TestFixtures.gerritCluster(context),
            TestFixtures.gitGcCronJob()),
        Arguments.of(
            TestFixtures.gitGarbageCollection(GITGC_SELECTED_PATCH),
            TestFixtures.gerritCluster(context),
            TestFixtures.gitGcCronJob(GITGC_CRONJOB_ARGS_SELECTED_PATCH)),
        Arguments.of(
            TestFixtures.gitGarbageCollection(GITGC_OPTIONS_PATCH),
            TestFixtures.gerritCluster(context),
            TestFixtures.gitGcCronJob(GITGC_CRONJOB_ARGS_OPTIONS_PATCH)),
        Arguments.of(
            TestFixtures.gitGarbageCollection(GITGC_SELECTED_PATCH, GITGC_OPTIONS_PATCH),
            TestFixtures.gerritCluster(context),
            TestFixtures.gitGcCronJob(
                GITGC_CRONJOB_ARGS_SELECTED_PATCH, GITGC_CRONJOB_ARGS_OPTIONS_APPEND_PATCH)),
        Arguments.of(
            TestFixtures.gitGarbageCollection(),
            TestFixtures.gerritCluster(context, GERRIT_CLUSTER_NFS_PATCH),
            TestFixtures.gitGcCronJob(GITGC_CRONJOB_NFS_PATCH)));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
