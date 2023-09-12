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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import io.getambassador.v2.Mapping;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterAmbassadorTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterAmbassadorComponentsCreated(
      String inputFile, Map<String, String> expectedOutputFileNames)
      throws ClassNotFoundException, NoSuchMethodException, InstantiationException,
          IllegalAccessException, InvocationTargetException {
    GerritNetwork gerritNetwork =
        ReconcilerUtils.loadYaml(GerritNetwork.class, this.getClass(), inputFile);

    for (Map.Entry<String, String> entry : expectedOutputFileNames.entrySet()) {
      String className = entry.getKey();
      String expectedOutputFile = entry.getValue();

      Class<?> clazz = Class.forName(className);
      Object dependentObject = clazz.getDeclaredConstructor(new Class[] {}).newInstance();

      if (dependentObject instanceof MappingDependentResourceInterface) {
        MappingDependentResourceInterface dependent =
            (MappingDependentResourceInterface) dependentObject;
        Mapping result = dependent.desired(gerritNetwork, null);
        Mapping expected =
            ReconcilerUtils.loadYaml(Mapping.class, this.getClass(), expectedOutputFile);
        assertThat(result.getSpec()).isEqualTo(expected.getSpec());
      }
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../../gerritnetwork_primary_replica_tls.yaml",
            Map.of(
                GerritClusterMappingGETReplica.class.getName(),
                    "mappingGETReplica_primary_replica.yaml",
                GerritClusterMappingPOSTReplica.class.getName(),
                    "mappingPOSTReplica_primary_replica.yaml",
                GerritClusterMappingPrimary.class.getName(), "mappingPrimary_primary_replica.yaml"
                // TODO: add tls
                )),
        Arguments.of(
            "../../gerritnetwork_primary_replica.yaml",
            Map.of(
                GerritClusterMappingGETReplica.class.getName(),
                    "mappingGETReplica_primary_replica.yaml",
                GerritClusterMappingPOSTReplica.class.getName(),
                    "mappingPOSTReplica_primary_replica.yaml",
                GerritClusterMappingPrimary.class.getName(),
                    "mappingPrimary_primary_replica.yaml")),
        Arguments.of(
            "../../gerritnetwork_primary.yaml",
            Map.of(GerritClusterMapping.class.getName(), "mapping_primary.yaml")),
        Arguments.of(
            "../../gerritnetwork_replica.yaml",
            Map.of(GerritClusterMapping.class.getName(), "mapping_replica.yaml")),
        Arguments.of(
            "../../gerritnetwork_receiver_replica.yaml",
            Map.of(
                GerritClusterMapping.class.getName(), "mapping_replica.yaml",
                GerritClusterMappingReceiver.class.getName(), "mapping_receiver.yaml")),
        Arguments.of(
            "../../gerritnetwork_receiver_replica_tls.yaml",
            Map.of(
                GerritClusterMapping.class.getName(), "mapping_replica.yaml",
                GerritClusterMappingReceiver.class.getName(), "mapping_receiver.yaml"
                // TODO: add tls
                )));
  }
}
