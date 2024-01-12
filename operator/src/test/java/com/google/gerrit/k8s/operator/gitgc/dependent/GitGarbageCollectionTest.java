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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.v1beta4.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta4.api.model.gitgc.GitGarbageCollection;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.net.HttpURLConnection;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GitGarbageCollectionTest {
  private static final String GET_GERRIT_CLUSTER_PATH =
      String.format(
          "/apis/%s/namespaces/gerrit/%s/gerrit",
          HasMetadata.getApiVersion(GerritCluster.class),
          HasMetadata.getPlural(GerritCluster.class));
  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    KubernetesDeserializer.registerCustomKind(
        "gerritoperator.google.com/v1beta1", "GerritCluster", GerritCluster.class);

    kubernetesServer.before();
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedCronJobCreated(
      String inputFile, String gerritClusterFile, String expectedCronJob) {
    GitGarbageCollection input =
        ReconcilerUtils.loadYaml(GitGarbageCollection.class, this.getClass(), inputFile);

    GerritCluster gerritCluster =
        ReconcilerUtils.loadYaml(GerritCluster.class, this.getClass(), gerritClusterFile);

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_GERRIT_CLUSTER_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, gerritCluster)
        .once();

    GitGarbageCollectionCronJob dependentCronjob = new GitGarbageCollectionCronJob();
    dependentCronjob.setKubernetesClient(kubernetesServer.getClient());
    assertThat(dependentCronjob.desired(input, null))
        .isEqualTo(ReconcilerUtils.loadYaml(CronJob.class, this.getClass(), expectedCronJob));
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../gitgc_all_default.yaml",
            "../gerritcluster_minimal.yaml",
            "cronjob_all_default.yaml"),
        Arguments.of(
            "../gitgc_selected_default.yaml",
            "../gerritcluster_minimal.yaml",
            "cronjob_selected_default.yaml"),
        Arguments.of(
            "../gitgc_all_options_enabled.yaml",
            "../gerritcluster_minimal.yaml",
            "cronjob_all_options_enabled.yaml"),
        Arguments.of(
            "../gitgc_selected_options_enabled.yaml",
            "../gerritcluster_minimal.yaml",
            "cronjob_selected_options_enabled.yaml"),
        Arguments.of(
            "../gitgc_all_default.yaml",
            "../gerritcluster_nfs_workaround.yaml",
            "cronjob_all_nfs_workaround.yaml"));
  }
}
