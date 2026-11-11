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

import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertConfigMapDataEqual;
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertEqualWithDiff;
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;
import static com.google.gerrit.k8s.operator.test.TestFixtures.CLUSTER_PATCH_CONFIGMAP;
import static com.google.gerrit.k8s.operator.test.TestFixtures.CLUSTER_PATCH_CONFIGMAP_PRIMARY_WITH_REPLICA;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_BASE;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_CLUSTER_INGRESS;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_GERRIT_INGRESS;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_MAINTENANCE_CLUSTER;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_PRIMARY_WITH_REPLICA_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_REPLICA_CLUSTER;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_REPLICA_GERRIT;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritConfigMap;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritClusterGerritsTest extends AbstractOperatorUnitTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritClusterGerritsCreated(
      KubernetesResourceFixture<GerritCluster> gerritClusterFixture,
      KubernetesResourceFixture<Gerrit> expectedGerritPrimaryFixture,
      KubernetesResourceFixture<Gerrit> expectedGerritReplicaFixture,
      KubernetesResourceFixture<GerritMaintenance> expectedGerritMaintenanceFixture,
      KubernetesResourceFixture<ConfigMap> expectedGerritConfigMapFixture) {
    GerritCluster gerritCluster = gerritClusterFixture.load();
    String ns = gerritCluster.getMetadata().getNamespace();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(
            client -> new GerritClusterReconciler(), client -> new GerritReconciler(client))) {
      mock.create(gerritCluster);
      for (GerritTemplate gerritTemplate : gerritCluster.getSpec().getGerrits()) {
        String name = gerritTemplate.getMetadata().getName();
        Gerrit actual = mock.awaitResource(Gerrit.class, ns, name);
        switch (gerritTemplate.getSpec().getMode()) {
          case PRIMARY:
            assertEqualWithDiff(actual.getSpec(), expectedGerritPrimaryFixture.load().getSpec());
            assertConfigMapDataEqual(
                mock.awaitResource(ConfigMap.class, ns, GerritConfigMap.getName(actual)),
                expectedGerritConfigMapFixture.load());
            break;
          case REPLICA:
            assertEqualWithDiff(actual.getSpec(), expectedGerritReplicaFixture.load().getSpec());
            break;
          default:
        }
      }
      if (expectedGerritMaintenanceFixture != null) {
        GerritMaintenance expectedMaintenance = expectedGerritMaintenanceFixture.load();
        assertUnordered(
            mock.awaitResource(
                    GerritMaintenance.class, ns, expectedMaintenance.getMetadata().getName())
                .getSpec(),
            expectedMaintenance.getSpec());
      }
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> context = GerritClusterGerritsTest.class;
    return Stream.of(
        Arguments.of(
            TestFixtures.gerritCluster(context, SHARED_PATCH_CLUSTER_INGRESS),
            TestFixtures.gerrit(context, SHARED_PATCH_GERRIT_INGRESS),
            null,
            null,
            TestFixtures.configMap(context, GERRIT_CONFIGMAP_BASE, CLUSTER_PATCH_CONFIGMAP)),
        Arguments.of(
            TestFixtures.gerritCluster(
                context, SHARED_PATCH_CLUSTER_INGRESS, SHARED_PATCH_REPLICA_CLUSTER),
            TestFixtures.gerrit(
                context, SHARED_PATCH_GERRIT_INGRESS, SHARED_PATCH_PRIMARY_WITH_REPLICA_GERRIT),
            TestFixtures.gerrit(context, SHARED_PATCH_GERRIT_INGRESS, SHARED_PATCH_REPLICA_GERRIT),
            null,
            TestFixtures.configMap(
                context, GERRIT_CONFIGMAP_BASE, CLUSTER_PATCH_CONFIGMAP_PRIMARY_WITH_REPLICA)),
        Arguments.of(
            TestFixtures.gerritCluster(
                context, SHARED_PATCH_CLUSTER_INGRESS, SHARED_PATCH_MAINTENANCE_CLUSTER),
            TestFixtures.gerrit(context, SHARED_PATCH_GERRIT_INGRESS),
            null,
            TestFixtures.gerritMaintenance(context),
            TestFixtures.configMap(context, GERRIT_CONFIGMAP_BASE, CLUSTER_PATCH_CONFIGMAP)));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
