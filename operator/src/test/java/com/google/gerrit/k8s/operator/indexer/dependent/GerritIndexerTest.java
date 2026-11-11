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

import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_CONFIGMAP_ES_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_CONFIGMAP_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_JOB_ES_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_JOB_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INDEXER_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_ES_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_OS_GERRIT;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritIndexerTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritIndexerWithoutSnapshotCreated(
      KubernetesResourceFixture<GerritIndexer> inputFixture,
      KubernetesResourceFixture<GerritCluster> gerritClusterFixture,
      KubernetesResourceFixture<Job> expectedJobFixture,
      KubernetesResourceFixture<ConfigMap> expectedConfigMapFixture) {
    GerritIndexer input = inputFixture.load();
    GerritCluster gerritCluster = gerritClusterFixture.load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GerritIndexerReconciler())) {
      mock.create(gerritCluster);
      mock.create(gerritCluster.getSpec().getGerrits().get(0).toGerrit(gerritCluster));
      String openSearchSecretRef = input.getSpec().getOpenSearchSecretRef();
      if (openSearchSecretRef != null && !openSearchSecretRef.isBlank()) {
        Secret tlsSecret =
            new SecretBuilder()
                .withNewMetadata()
                .withName(openSearchSecretRef)
                .withNamespace(input.getMetadata().getNamespace())
                .endMetadata()
                .withData(
                    Map.of(
                        "ca.crt", "Y2VydA==",
                        "truststore.jks", "dHJ1c3RzdG9yZQ=="))
                .build();
        mock.create(tlsSecret);
      }
      mock.create(input);
      String ns = input.getMetadata().getNamespace();
      expectedJobFixture.assertEqualTo(
          mock.awaitResource(Job.class, ns, GerritIndexerJob.getName(input)));
      expectedConfigMapFixture.assertEqualTo(
          mock.awaitResource(ConfigMap.class, ns, GerritIndexerConfigMap.getName(input)));
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
            TestFixtures.gerritIndexer(SHARED_PATCH_OS_GERRIT, INDEXER_OS_PATCH),
            TestFixtures.gerritCluster(context),
            TestFixtures.indexerJob(INDEXER_JOB_ES_PATCH, INDEXER_JOB_OS_PATCH),
            TestFixtures.indexerConfigMap(INDEXER_CONFIGMAP_OS_PATCH)));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
