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
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.Gateway;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterIstioTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterIstioComponentsCreated(
      KubernetesResourceFixture<GerritNetwork> inputFixture,
      KubernetesResourceFixture<Gateway> expectedGateway,
      KubernetesResourceFixture<VirtualService> expectedVirtualService,
      List<KubernetesResourceFixture<DestinationRule>> expectedDestRules) {
    GerritNetwork gerritNetwork = inputFixture.load();
    String ns = gerritNetwork.getMetadata().getNamespace();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GerritIstioReconciler())) {
      mock.create(gerritNetwork);
      expectedGateway.assertEqualTo(
          mock.awaitResource(Gateway.class, ns, expectedGateway.getName()));
      expectedVirtualService.assertEqualTo(
          mock.awaitResource(VirtualService.class, ns, expectedVirtualService.getName()));
      for (KubernetesResourceFixture<DestinationRule> fixture : expectedDestRules) {
        fixture.assertEqualTo(mock.awaitResource(DestinationRule.class, ns, fixture.getName()));
      }
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
