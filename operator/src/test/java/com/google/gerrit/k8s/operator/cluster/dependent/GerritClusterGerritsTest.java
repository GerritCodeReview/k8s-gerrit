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

import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertEqualWithDiff;
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_MAINTENANCE_CLUSTER;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_PRIMARY_WITH_REPLICA_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_REPLICA_CLUSTER;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_REPLICA_GERRIT;

import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterGerritsTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterGerritsCreated(
      KubernetesResourceFixture<GerritCluster> gerritClusterFixture,
      KubernetesResourceFixture<Gerrit> expectedGerritPrimaryFixture,
      KubernetesResourceFixture<Gerrit> expectedGerritReplicaFixture,
      KubernetesResourceFixture<GerritMaintenance> expectedGerritMaintenanceFixture)
      throws ConfigInvalidException {
    OperatorContext.createInstance(Constants.ClusterMode.HIGH_AVAILABILITY, "cluster.local");
    GerritCluster gerritCluster = gerritClusterFixture.load();

    ClusterManagedGerrit clusterManagedGerrit = new ClusterManagedGerrit();
    Map<String, Gerrit> gerrits = clusterManagedGerrit.desiredResources(gerritCluster, null);
    for (Gerrit g : gerrits.values()) {
      switch (g.getSpec().getMode()) {
        case PRIMARY:
          {
            Gerrit expected = expectedGerritPrimaryFixture.load();
            assertEqualWithDiff(g.getSpec(), expected.getSpec());
          }
          break;
        case REPLICA:
          {
            Gerrit expected = expectedGerritReplicaFixture.load();
            assertEqualWithDiff(g.getSpec(), expected.getSpec());
          }
          break;
        default:
      }
    }
    if (expectedGerritMaintenanceFixture != null) {
      GerritMaintenance expectedGerritMaintenance = expectedGerritMaintenanceFixture.load();
      ClusterManagedGerritMaintenance gerritMaintenanceReconciler =
          new ClusterManagedGerritMaintenance();
      assertUnordered(
          gerritMaintenanceReconciler.desired(gerritCluster, null), expectedGerritMaintenance);
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> context = GerritClusterGerritsTest.class;
    return Stream.of(
        Arguments.of(TestFixtures.gerritCluster(context), TestFixtures.gerrit(context), null, null),
        Arguments.of(
            TestFixtures.gerritCluster(context, SHARED_PATCH_REPLICA_CLUSTER),
            TestFixtures.gerrit(context, SHARED_PATCH_PRIMARY_WITH_REPLICA_GERRIT),
            TestFixtures.gerrit(context, SHARED_PATCH_REPLICA_GERRIT),
            null),
        Arguments.of(
            TestFixtures.gerritCluster(context, SHARED_PATCH_MAINTENANCE_CLUSTER),
            TestFixtures.gerrit(context),
            null,
            TestFixtures.gerritMaintenance(context)));
  }
}
