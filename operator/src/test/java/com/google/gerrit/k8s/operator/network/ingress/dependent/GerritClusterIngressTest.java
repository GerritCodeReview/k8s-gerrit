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

import com.google.gerrit.k8s.operator.v1beta4.api.model.network.GerritNetwork;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterIngressTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterIngressCreated(String inputFile, String expectedOutputFile) {
    GerritClusterIngress dependent = new GerritClusterIngress();
    Ingress result =
        dependent.desired(
            ReconcilerUtils.loadYaml(GerritNetwork.class, this.getClass(), inputFile), null);
    Ingress expected = ReconcilerUtils.loadYaml(Ingress.class, this.getClass(), expectedOutputFile);
    assertThat(result.getSpec()).isEqualTo(expected.getSpec());
    assertThat(result.getMetadata().getAnnotations())
        .containsExactlyEntriesIn(expected.getMetadata().getAnnotations());
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../../gerritnetwork_primary_replica_tls.yaml", "ingress_primary_replica_tls.yaml"),
        Arguments.of("../../gerritnetwork_primary_replica.yaml", "ingress_primary_replica.yaml"),
        Arguments.of("../../gerritnetwork_primary.yaml", "ingress_primary.yaml"),
        Arguments.of("../../gerritnetwork_replica.yaml", "ingress_replica.yaml"),
        Arguments.of("../../gerritnetwork_receiver_replica.yaml", "ingress_receiver_replica.yaml"),
        Arguments.of(
            "../../gerritnetwork_receiver_replica_tls.yaml", "ingress_receiver_replica_tls.yaml"));
  }
}
