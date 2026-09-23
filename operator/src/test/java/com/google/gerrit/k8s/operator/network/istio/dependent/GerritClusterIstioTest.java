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

package com.google.gerrit.k8s.operator.network.istio.dependent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_ISTIO_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_RECEIVER_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_REPLICA_SSH_PORT_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_SSH_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRITNETWORK_TLS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_GATEWAY_SSH_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_GATEWAY_SSH_REPLICA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_GATEWAY_TLS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_RECEIVER_MATCH_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_RECEIVER_REPLICA_HTTP_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_REPLICA_READ_MATCH_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_TCP_PRIMARY_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.ISTIO_VIRTUALSERVICE_TCP_REPLICA_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritClusterIstioTest {

  @BeforeAll
  public void setup() {
    OperatorContext.createInstance(ClusterMode.HIGH_AVAILABILITY, "cluster.local");
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterIstioComponentsCreated(
      KubernetesResourceFixture<GerritNetwork> inputFixture,
      KubernetesResourceFixture<Gateway> expectedGateway,
      KubernetesResourceFixture<VirtualService> expectedVirtualService,
      List<KubernetesResourceFixture<DestinationRule>> expectedDestRules) {
    GerritNetwork gerritNetwork = inputFixture.load();

    assertUnordered(
        new GerritClusterIstioGateway().desired(gerritNetwork, null).getSpec(),
        expectedGateway.load().getSpec());

    assertUnordered(
        new GerritIstioVirtualService().desired(gerritNetwork, null).getSpec(),
        expectedVirtualService.load().getSpec());

    Map<String, DestinationRule> destRuleResult =
        new GerritIstioDestinationRule().desiredResources(gerritNetwork, null);

    assertThat(destRuleResult).hasSize(expectedDestRules.size());
    for (KubernetesResourceFixture<DestinationRule> fixture : expectedDestRules) {
      DestinationRule expected = fixture.load();
      assertThat(destRuleResult).containsKey(expected.getMetadata().getName());
      assertUnordered(
          destRuleResult.get(expected.getMetadata().getName()).getSpec(), expected.getSpec());
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_TLS_PATCH),
            TestFixtures.istioGateway(ISTIO_GATEWAY_TLS_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH,
                ISTIO_VIRTUALSERVICE_REPLICA_READ_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH),
            concat(
                TestFixtures.istioDestinationRulesPrimary(),
                TestFixtures.istioDestinationRulesReplica())),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH),
            TestFixtures.istioGateway(),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH,
                ISTIO_VIRTUALSERVICE_REPLICA_READ_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH),
            concat(
                TestFixtures.istioDestinationRulesPrimary(),
                TestFixtures.istioDestinationRulesReplica())),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_ISTIO_PATCH, GERRITNETWORK_PRIMARY_PATCH),
            TestFixtures.istioGateway(),
            TestFixtures.istioVirtualService(ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH),
            TestFixtures.istioDestinationRulesPrimary()),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_ISTIO_PATCH, GERRITNETWORK_REPLICA_PATCH),
            TestFixtures.istioGateway(),
            TestFixtures.istioVirtualService(ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH),
            TestFixtures.istioDestinationRulesReplica()),
        Arguments.of(
            TestFixtures.gerritNetwork(GERRITNETWORK_RECEIVER_PATCH),
            TestFixtures.istioGateway(),
            TestFixtures.istioVirtualService(ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH),
            List.of()),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_RECEIVER_PATCH),
            TestFixtures.istioGateway(),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_REPLICA_HTTP_PATCH),
            TestFixtures.istioDestinationRulesReplica()),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_RECEIVER_PATCH,
                GERRITNETWORK_TLS_PATCH),
            TestFixtures.istioGateway(ISTIO_GATEWAY_TLS_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_REPLICA_HTTP_PATCH),
            TestFixtures.istioDestinationRulesReplica()),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH, GERRITNETWORK_SSH_PATCH, GERRITNETWORK_PRIMARY_PATCH),
            TestFixtures.istioGateway(ISTIO_GATEWAY_SSH_PRIMARY_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH, ISTIO_VIRTUALSERVICE_TCP_PRIMARY_PATCH),
            TestFixtures.istioDestinationRulesPrimary()),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_SSH_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_REPLICA_SSH_PORT_PATCH),
            TestFixtures.istioGateway(ISTIO_GATEWAY_SSH_REPLICA_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH, ISTIO_VIRTUALSERVICE_TCP_REPLICA_PATCH),
            TestFixtures.istioDestinationRulesReplica()),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_SSH_PATCH,
                GERRITNETWORK_PRIMARY_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_REPLICA_SSH_PORT_PATCH),
            TestFixtures.istioGateway(
                ISTIO_GATEWAY_SSH_PRIMARY_PATCH, ISTIO_GATEWAY_SSH_REPLICA_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH,
                ISTIO_VIRTUALSERVICE_REPLICA_READ_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH,
                ISTIO_VIRTUALSERVICE_TCP_PRIMARY_PATCH,
                ISTIO_VIRTUALSERVICE_TCP_REPLICA_PATCH),
            concat(
                TestFixtures.istioDestinationRulesPrimary(),
                TestFixtures.istioDestinationRulesReplica())),
        Arguments.of(
            TestFixtures.gerritNetwork(
                GERRITNETWORK_ISTIO_PATCH,
                GERRITNETWORK_SSH_PATCH,
                GERRITNETWORK_REPLICA_PATCH,
                GERRITNETWORK_REPLICA_SSH_PORT_PATCH,
                GERRITNETWORK_RECEIVER_PATCH),
            TestFixtures.istioGateway(ISTIO_GATEWAY_SSH_REPLICA_PATCH),
            TestFixtures.istioVirtualService(
                ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_MATCH_PATCH,
                ISTIO_VIRTUALSERVICE_RECEIVER_REPLICA_HTTP_PATCH,
                ISTIO_VIRTUALSERVICE_TCP_REPLICA_PATCH),
            TestFixtures.istioDestinationRulesReplica()));
  }

  @SafeVarargs
  private static <T> List<T> concat(List<T>... lists) {
    return Stream.of(lists).flatMap(List::stream).toList();
  }
}
