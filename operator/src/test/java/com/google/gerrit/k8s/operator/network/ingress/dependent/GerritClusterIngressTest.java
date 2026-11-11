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

package com.google.gerrit.k8s.operator.network.ingress.dependent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_RECEIVER_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_TLS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_PATHS_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_PATHS_PRIMARY_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_PATHS_RECEIVER_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_PATHS_RECEIVER_WRITE_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_PATHS_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_SNIPPET_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_SNIPPET_PRIMARY_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_SNIPPET_RECEIVER_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.INGRESS_TLS_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.network.ingress.GerritIngressReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterIngressTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterIngressCreated(
      KubernetesResourceFixture<GerritNetwork> inputFixture,
      KubernetesResourceFixture<Ingress> expectedIngressFixture) {
    GerritNetwork gerritNetwork = inputFixture.load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GerritIngressReconciler())) {
      mock.create(gerritNetwork);
      String ns = gerritNetwork.getMetadata().getNamespace();
      Ingress actual = mock.awaitResource(Ingress.class, ns, expectedIngressFixture.getName());
      expectedIngressFixture.assertEqualTo(actual);
      // Also verify the nginx annotations set by the dependent (server adds extra annotations
      // such as javaoperatorsdk.io/previous, so use containsAtLeastEntriesIn).
      assertThat(actual.getMetadata().getAnnotations())
          .containsAtLeastEntriesIn(expectedIngressFixture.load().getMetadata().getAnnotations());
    }
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_PRIMARY_PATCH, GERRITNETWORK_REPLICA_PATCH, GERRITNETWORK_TLS_PATCH),
            TestFixtures.nginxIngress(
                INGRESS_SNIPPET_PRIMARY_REPLICA_PATCH,
                INGRESS_PATHS_PRIMARY_PATCH,
                INGRESS_PATHS_PRIMARY_REPLICA_PATCH,
                INGRESS_TLS_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_PRIMARY_PATCH, GERRITNETWORK_REPLICA_PATCH),
            TestFixtures.nginxIngress(
                INGRESS_SNIPPET_PRIMARY_REPLICA_PATCH,
                INGRESS_PATHS_PRIMARY_PATCH,
                INGRESS_PATHS_PRIMARY_REPLICA_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_PRIMARY_PATCH),
            TestFixtures.nginxIngress(INGRESS_SNIPPET_PRIMARY_PATCH, INGRESS_PATHS_PRIMARY_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_REPLICA_PATCH),
            TestFixtures.nginxIngress(INGRESS_PATHS_REPLICA_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_RECEIVER_PATCH),
            TestFixtures.nginxIngress(INGRESS_PATHS_RECEIVER_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_RECEIVER_PATCH, GERRITNETWORK_REPLICA_PATCH),
            TestFixtures.nginxIngress(
                INGRESS_SNIPPET_RECEIVER_PATCH,
                INGRESS_PATHS_RECEIVER_WRITE_PATCH,
                INGRESS_PATHS_REPLICA_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_RECEIVER_PATCH, GERRITNETWORK_REPLICA_PATCH, GERRITNETWORK_TLS_PATCH),
            TestFixtures.nginxIngress(
                INGRESS_SNIPPET_RECEIVER_PATCH,
                INGRESS_PATHS_RECEIVER_WRITE_PATCH,
                INGRESS_PATHS_REPLICA_PATCH,
                INGRESS_TLS_PATCH)));
  }
}
