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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.AMBASSADOR_HOST_TLS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_AMBASSADOR_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_CREATE_HOST_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_RECEIVER_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_TLS_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import com.google.gerrit.k8s.operator.test.TestFixtures.ExpectedMapping;
import io.getambassador.v2.Host;
import io.getambassador.v2.TLSContext;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritClusterAmbassadorTest {

  @BeforeAll
  public void setup() {
    OperatorContext.createInstance(ClusterMode.HIGH_AVAILABILITY, "cluster.local");
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterAmbassadorComponentsCreated(
      KubernetesResourceFixture<GerritNetwork> inputFixture,
      List<ExpectedMapping> expectedMappings,
      KubernetesResourceFixture<TLSContext> expectedTlsContext,
      KubernetesResourceFixture<Host> expectedHost)
      throws NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException {
    GerritNetwork gerritNetwork = inputFixture.load();

    for (ExpectedMapping expected : expectedMappings) {
      MappingDependentResourceInterface dependent =
          (MappingDependentResourceInterface)
              expected.dependentClass().getDeclaredConstructor().newInstance();
      assertUnordered(
          dependent.desired(gerritNetwork, null).getSpec(), expected.fixture().load().getSpec());
    }

    if (expectedTlsContext != null) {
      assertUnordered(
          new GerritClusterTLSContext().desired(gerritNetwork, null).getSpec(),
          expectedTlsContext.load().getSpec());
    }

    if (expectedHost != null) {
      assertUnordered(
          new GerritClusterHost().desired(gerritNetwork, null).getSpec(),
          expectedHost.load().getSpec());
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_TLS_PATCH),
            List.of(
                TestFixtures.ambassadorMappingGetReplica(),
                TestFixtures.ambassadorMappingPostReplica(),
                TestFixtures.ambassadorMappingPrimary()),
            TestFixtures.ambassadorTlsContext(),
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_TLS_PATCH,
                GERRITNETWORK_CREATE_HOST_PATCH),
            List.of(
                TestFixtures.ambassadorMappingGetReplica(),
                TestFixtures.ambassadorMappingPostReplica(),
                TestFixtures.ambassadorMappingPrimary()),
            TestFixtures.ambassadorTlsContext(),
            TestFixtures.ambassadorHost(AMBASSADOR_HOST_TLS_PATCH)),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH),
            List.of(
                TestFixtures.ambassadorMappingGetReplica(),
                TestFixtures.ambassadorMappingPostReplica(),
                TestFixtures.ambassadorMappingPrimary()),
            null,
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_CREATE_HOST_PATCH),
            List.of(
                TestFixtures.ambassadorMappingGetReplica(),
                TestFixtures.ambassadorMappingPostReplica(),
                TestFixtures.ambassadorMappingPrimary()),
            null,
            TestFixtures.ambassadorHost()),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_AMBASSADOR_PATCH, GERRITNETWORK_PRIMARY_PATCH),
            List.of(TestFixtures.ambassadorMappingDefaultPrimary()),
            null,
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_AMBASSADOR_PATCH, GERRITNETWORK_REPLICA_PATCH),
            List.of(TestFixtures.ambassadorMappingDefaultReplica()),
            null,
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_RECEIVER_PATCH),
            List.of(
                TestFixtures.ambassadorMappingDefaultReplica(),
                TestFixtures.ambassadorMappingReceiver(),
                TestFixtures.ambassadorMappingGetReceiver()),
            null,
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH, GERRITNETWORK_RECEIVER_PATCH),
            List.of(TestFixtures.ambassadorMappingReceiverOnly()),
            null,
            null),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_AMBASSADOR_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_RECEIVER_PATCH,
                GERRITNETWORK_TLS_PATCH),
            List.of(
                TestFixtures.ambassadorMappingDefaultReplica(),
                TestFixtures.ambassadorMappingReceiver(),
                TestFixtures.ambassadorMappingGetReceiver()),
            TestFixtures.ambassadorTlsContext(),
            null));
  }
}
