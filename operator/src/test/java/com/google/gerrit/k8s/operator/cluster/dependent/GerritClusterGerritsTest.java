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
package com.google.gerrit.k8s.operator.cluster.dependent;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterGerritsTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterGerritsCreated(
      String inputFile,
      String expectedGerritPrimaryOutputFile,
      String expectedGerritReplicaOutputFile,
      String expectedGerritMaintenanceOutputFile)
      throws ConfigInvalidException {

    OperatorContext.createInstance();
    GerritCluster gerritCluster =
        ReconcilerUtils.loadYaml(GerritCluster.class, this.getClass(), inputFile);
    ClusterManagedGerrit clusterManagedGerrit = new ClusterManagedGerrit();
    Map<String, Gerrit> gerrits = clusterManagedGerrit.desiredResources(gerritCluster, null);
    for (Gerrit g : gerrits.values()) {
      switch (g.getSpec().getMode()) {
        case PRIMARY:
          {
            Gerrit expected =
                ReconcilerUtils.loadYaml(
                    Gerrit.class, this.getClass(), expectedGerritPrimaryOutputFile);
            assertThat(g.getSpec().getSshdAdvertisedReadPort() == 39418);

            assertThat(g.getSpec()).isEqualTo(expected.getSpec());

            GerritConfigMap gcm = new GerritConfigMap();
            ConfigMap configMap = gcm.desired(g, null);
            String configBlob = configMap.getData().get("gerrit.config");
            Config gerritConfig = new Config();
            gerritConfig.fromText(configBlob);
            assertThat(gerritConfig.getString("sshd", null, "listenAddress")).isEqualTo("*:29418");
            assertThat(gerritConfig.getString("sshd", null, "advertisedAddress"))
                .isEqualTo("example.com:39418");
          }
          break;
        case REPLICA:
          {
            Gerrit expected =
                ReconcilerUtils.loadYaml(
                    Gerrit.class, this.getClass(), expectedGerritReplicaOutputFile);
            assertThat(g.getSpec()).isEqualTo(expected.getSpec());
          }
          break;
        default:
      }
    }
    GerritMaintenance expectedGerritMaintenance =
        ReconcilerUtils.loadYaml(
            GerritMaintenance.class, this.getClass(), expectedGerritMaintenanceOutputFile);
    ClusterManagedGerritMaintenance gerritMaintenanceReconciler =
        new ClusterManagedGerritMaintenance();
    assertThat(gerritMaintenanceReconciler.desired(gerritCluster, null))
        .isEqualTo(expectedGerritMaintenance);
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../gerritcluster_primary_replica.yaml",
            "gerrit_primary.yaml",
            "gerrit_replica.yaml",
            "gerrit_maintenance.yaml"));
  }
}
