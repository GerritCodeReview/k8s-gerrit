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

import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterIstioTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterIstioComponentsCreated(
      String inputFile, String expectedGatewayOutputFile, String expectedVirtualServiceOutputFile) {
    OperatorContext.createInstance(false);
    GerritNetwork gerritNetwork =
        ReconcilerUtils.loadYaml(GerritNetwork.class, this.getClass(), inputFile);
    GerritClusterIstioGateway gatewayDependent = new GerritClusterIstioGateway();
    Gateway gatewayResult = gatewayDependent.desired(gerritNetwork, null);
    Gateway expectedGateway =
        ReconcilerUtils.loadYaml(Gateway.class, this.getClass(), expectedGatewayOutputFile);
    assertThat(gatewayResult.getSpec()).isEqualTo(expectedGateway.getSpec());

    GerritIstioVirtualService virtualServiceDependent = new GerritIstioVirtualService();
    VirtualService virtualServiceResult = virtualServiceDependent.desired(gerritNetwork, null);
    VirtualService expectedVirtualService =
        ReconcilerUtils.loadYaml(
            VirtualService.class, this.getClass(), expectedVirtualServiceOutputFile);
    assertThat(virtualServiceResult.getSpec()).isEqualTo(expectedVirtualService.getSpec());
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../../gerritnetwork_primary_replica_tls.yaml",
            "gateway_tls.yaml",
            "virtualservice_primary_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_primary_replica.yaml",
            "gateway.yaml",
            "virtualservice_primary_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_primary.yaml", "gateway.yaml", "virtualservice_primary.yaml"),
        Arguments.of(
            "../../gerritnetwork_replica.yaml", "gateway.yaml", "virtualservice_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_receiver_replica.yaml",
            "gateway.yaml",
            "virtualservice_receiver_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_receiver_replica_tls.yaml",
            "gateway_tls.yaml",
            "virtualservice_receiver_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_primary_ssh.yaml",
            "gateway_primary_ssh.yaml",
            "virtualservice_primary_ssh.yaml"),
        Arguments.of(
            "../../gerritnetwork_replica_ssh.yaml",
            "gateway_replica_ssh.yaml",
            "virtualservice_replica_ssh.yaml"),
        Arguments.of(
            "../../gerritnetwork_primary_replica_ssh.yaml",
            "gateway_primary_replica_ssh.yaml",
            "virtualservice_primary_replica_ssh.yaml"),
        Arguments.of(
            "../../gerritnetwork_receiver_replica_ssh.yaml",
            "gateway_receiver_replica_ssh.yaml",
            "virtualservice_receiver_replica_ssh.yaml"));
  }
}
