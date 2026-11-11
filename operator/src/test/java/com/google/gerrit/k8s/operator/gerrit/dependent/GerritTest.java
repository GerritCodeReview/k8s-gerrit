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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_ES_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_HA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_INGRESS_HTTPS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_INGRESS_HTTP_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_CONFIGMAP_OS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_INIT_CONFIGMAP_HA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_SERVICE_HA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_STATEFULSET_HA_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.GERRIT_STATEFULSET_INDEX_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_ES_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_HA_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_INGRESS_HTTPS_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_INGRESS_HTTP_GERRIT;
import static com.google.gerrit.k8s.operator.test.TestFixtures.SHARED_PATCH_OS_GERRIT;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritComponentsCreated(
      KubernetesResourceFixture<Gerrit> inputFixture,
      KubernetesResourceFixture<ConfigMap> expectedGerritConfigMapFixture,
      KubernetesResourceFixture<ConfigMap> expectedGerritInitConfigMapFixture,
      KubernetesResourceFixture<StatefulSet> expectedStatefulSetFixture,
      KubernetesResourceFixture<Service> expectedServiceFixture,
      KubernetesResourceFixture<Service> expectedHeadlessServiceFixture) {
    Gerrit gerrit = inputFixture.load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new GerritReconciler(client))) {
      mock.create(gerrit);
      String ns = gerrit.getMetadata().getNamespace();
      expectedGerritConfigMapFixture.assertEqualTo(
          mock.awaitResource(ConfigMap.class, ns, GerritConfigMap.getName(gerrit)));
      expectedGerritInitConfigMapFixture.assertEqualTo(
          mock.awaitResource(ConfigMap.class, ns, GerritInitConfigMap.getName(gerrit)));
      expectedStatefulSetFixture.assertEqualTo(
          mock.awaitResource(StatefulSet.class, ns, GerritStatefulSet.getName(gerrit)));
      expectedServiceFixture.assertEqualTo(
          mock.awaitResource(Service.class, ns, new GerritService().getName(gerrit)));
      expectedHeadlessServiceFixture.assertEqualTo(
          mock.awaitResource(Service.class, ns, new GerritHeadlessService().getName(gerrit)));
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    Class<?> ctx = GerritTest.class;
    return Stream.of(
        Arguments.of(
            TestFixtures.gerrit(ctx),
            TestFixtures.gerritConfigMap(),
            TestFixtures.gerritInitConfigMap(),
            TestFixtures.gerritStatefulSet(),
            TestFixtures.gerritService(),
            TestFixtures.gerritHeadlessService()),
        Arguments.of(
            TestFixtures.gerrit(ctx, SHARED_PATCH_HA_GERRIT),
            TestFixtures.gerritConfigMap(GERRIT_CONFIGMAP_HA_PATCH),
            TestFixtures.gerritInitConfigMap(GERRIT_INIT_CONFIGMAP_HA_PATCH),
            TestFixtures.gerritStatefulSet(GERRIT_STATEFULSET_HA_PATCH),
            TestFixtures.gerritService(GERRIT_SERVICE_HA_PATCH),
            TestFixtures.gerritHeadlessService(GERRIT_SERVICE_HA_PATCH)),
        Arguments.of(
            TestFixtures.gerrit(ctx, SHARED_PATCH_HA_GERRIT, SHARED_PATCH_ES_GERRIT),
            TestFixtures.gerritConfigMap(GERRIT_CONFIGMAP_HA_PATCH, GERRIT_CONFIGMAP_ES_PATCH),
            TestFixtures.gerritInitConfigMap(GERRIT_INIT_CONFIGMAP_HA_PATCH),
            TestFixtures.gerritStatefulSet(
                GERRIT_STATEFULSET_HA_PATCH, GERRIT_STATEFULSET_INDEX_PATCH),
            TestFixtures.gerritService(GERRIT_SERVICE_HA_PATCH),
            TestFixtures.gerritHeadlessService(GERRIT_SERVICE_HA_PATCH)),
        Arguments.of(
            TestFixtures.gerrit(ctx, SHARED_PATCH_HA_GERRIT, SHARED_PATCH_OS_GERRIT),
            TestFixtures.gerritConfigMap(GERRIT_CONFIGMAP_HA_PATCH, GERRIT_CONFIGMAP_OS_PATCH),
            TestFixtures.gerritInitConfigMap(GERRIT_INIT_CONFIGMAP_HA_PATCH),
            TestFixtures.gerritStatefulSet(
                GERRIT_STATEFULSET_HA_PATCH, GERRIT_STATEFULSET_INDEX_PATCH),
            TestFixtures.gerritService(GERRIT_SERVICE_HA_PATCH),
            TestFixtures.gerritHeadlessService(GERRIT_SERVICE_HA_PATCH)),
        Arguments.of(
            TestFixtures.gerrit(ctx, SHARED_PATCH_INGRESS_HTTP_GERRIT),
            TestFixtures.gerritConfigMap(GERRIT_CONFIGMAP_INGRESS_HTTP_PATCH),
            TestFixtures.gerritInitConfigMap(),
            TestFixtures.gerritStatefulSet(),
            TestFixtures.gerritService(),
            TestFixtures.gerritHeadlessService()),
        Arguments.of(
            TestFixtures.gerrit(ctx, SHARED_PATCH_HA_GERRIT, SHARED_PATCH_INGRESS_HTTPS_GERRIT),
            TestFixtures.gerritConfigMap(
                GERRIT_CONFIGMAP_HA_PATCH, GERRIT_CONFIGMAP_INGRESS_HTTPS_PATCH),
            TestFixtures.gerritInitConfigMap(GERRIT_INIT_CONFIGMAP_HA_PATCH),
            TestFixtures.gerritStatefulSet(GERRIT_STATEFULSET_HA_PATCH),
            TestFixtures.gerritService(GERRIT_SERVICE_HA_PATCH),
            TestFixtures.gerritHeadlessService(GERRIT_SERVICE_HA_PATCH)));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
