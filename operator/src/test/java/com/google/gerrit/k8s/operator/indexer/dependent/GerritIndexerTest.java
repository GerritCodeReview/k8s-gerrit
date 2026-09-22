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

package com.google.gerrit.k8s.operator.indexer.dependent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_CONFIGMAP_ES_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_CONFIGMAP_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_JOB_ES_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_JOB_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_ES_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_OS_GERRIT;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GenericRetryExecution;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritIndexerTest {
  private static final String GET_GERRIT_CLUSTER_PATH =
      String.format(
          "/apis/%s/namespaces/gerrit/%s/gerrit",
          HasMetadata.getApiVersion(GerritCluster.class),
          HasMetadata.getPlural(GerritCluster.class));
  private static final String GET_GERRIT_PATH =
      String.format(
          "/apis/%s/namespaces/gerrit/%s/gerrit",
          HasMetadata.getApiVersion(Gerrit.class), HasMetadata.getPlural(Gerrit.class));
  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    OperatorContext.createInstance(ClusterMode.HIGH_AVAILABILITY, "cluster.local");
    kubernetesServer.before();
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritIndexerWithoutSnapshotCreated(
      KubernetesResourceFixture<GerritIndexer> inputFixture,
      KubernetesResourceFixture<GerritCluster> gerritClusterFixture,
      KubernetesResourceFixture<Job> expectedJobFixture,
      KubernetesResourceFixture<ConfigMap> expectedConfigMapFixture) {
    GerritIndexer input = inputFixture.load();
    GerritCluster gerritCluster = gerritClusterFixture.load();

    stubs(gerritCluster);

    Context<GerritIndexer> context = getContext(new GerritIndexerReconciler(), input);

    assertUnordered(new GerritIndexerJob().desired(input, context), expectedJobFixture.load());
    assertDesiredConfigMapCreated(
        new GerritIndexerConfigMap().desired(input, context), expectedConfigMapFixture.load());
  }

  private void stubs(GerritCluster gerritCluster) {
    Secret testSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withName("opensearch-tls-config")
            .withNamespace("gerrit")
            .endMetadata()
            .withData(
                Map.of(
                    "ca.crt", "Y2VydA==",
                    "truststore.jks", "dHJ1c3RzdG9yZQ=="))
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath("/api/v1/namespaces/gerrit/secrets/opensearch-tls-config")
        .andReturn(HttpURLConnection.HTTP_OK, testSecret)
        .always();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_GERRIT_CLUSTER_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, gerritCluster)
        .times(2);

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_GERRIT_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK,
            gerritCluster.getSpec().getGerrits().get(0).toGerrit(gerritCluster))
        .times(2);
  }

  private Context<GerritIndexer> getContext(
      Reconciler<GerritIndexer> reconciler, GerritIndexer primary) {
    Controller<GerritIndexer> controller =
        new Controller<GerritIndexer>(
            reconciler,
            new BaseConfigurationService().getConfigurationFor(reconciler),
            kubernetesServer.getClient());

    return new DefaultContext<GerritIndexer>(
        new GenericRetryExecution(new GenericRetry()), controller, primary);
  }

  private void assertDesiredConfigMapCreated(ConfigMap actual, ConfigMap expected) {
    assertThat(actual.getMetadata()).isEqualTo(expected.getMetadata());
    assertThat(actual.getData().keySet()).isEqualTo(expected.getData().keySet());
    for (Map.Entry<String, String> file : actual.getData().entrySet()) {
      assertThat(file.getValue().replaceAll("\\s+", "").trim())
          .isEqualTo(expected.getData().get(file.getKey()).replaceAll("\\s+", "").trim());
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> context = GerritIndexerTest.class;
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritIndexer(),
            TestFixtures.gerritCluster(context),
            TestFixtures.indexerJob(),
            TestFixtures.indexerConfigMap()),
        Arguments.of(
            TestFixtures.gerritIndexer(SHARED_PATCH_ES_GERRIT),
            TestFixtures.gerritCluster(context),
            TestFixtures.indexerJob(INDEXER_JOB_ES_PATCH),
            TestFixtures.indexerConfigMap(INDEXER_CONFIGMAP_ES_PATCH)),
        Arguments.of(
            TestFixtures.gerritIndexer(),
            TestFixtures.gerritCluster(context, SHARED_PATCH_ES_GERRIT),
            TestFixtures.indexerJob(INDEXER_JOB_ES_PATCH),
            TestFixtures.indexerConfigMap(INDEXER_CONFIGMAP_ES_PATCH)),
        Arguments.of(
            TestFixtures.gerritIndexer(SHARED_PATCH_OS_GERRIT, INDEXER_OS_PATCH),
            TestFixtures.gerritCluster(context),
            TestFixtures.indexerJob(INDEXER_JOB_ES_PATCH, INDEXER_JOB_OS_PATCH),
            TestFixtures.indexerConfigMap(INDEXER_CONFIGMAP_OS_PATCH)));
  }
}
