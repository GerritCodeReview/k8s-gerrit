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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedIncomingReplicationTask;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.IncomingReplicationTaskReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class IncomingReplicationTaskTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedIncomingReplicationTaskComponentsCreated(
      KubernetesResourceFixture<GerritCluster> inputFixture,
      KubernetesResourceFixture<IncomingReplicationTask> expectedTaskFixture,
      KubernetesResourceFixture<CronJob> expectedCronJobFixture,
      KubernetesResourceFixture<ConfigMap> expectedConfigMapFixture)
      throws Exception {
    GerritCluster input = inputFixture.load();
    String ns = input.getMetadata().getNamespace();
    // Derive the secretRef from the cluster spec so the secret can be pre-created before
    // IncomingReplicationTaskReconciler runs. ClusterManagedIncomingReplicationTask is used here
    // only as a pure computation (no K8s calls), not for spec verification.
    String secretRef =
        new ClusterManagedIncomingReplicationTask()
            .desiredResources(input, null)
            .get(expectedTaskFixture.getName())
            .getSpec()
            .getSecretRef();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(
            client -> new GerritClusterReconciler(),
            client -> new IncomingReplicationTaskReconciler())) {
      mock.create(
          new SecretBuilder()
              .withNewMetadata()
              .withName(secretRef)
              .withNamespace(ns)
              .endMetadata()
              .withData(Map.of(".netrc", "c2VjcmV0Cg=="))
              .build());
      mock.create(input);
      // GerritClusterReconciler creates the IncomingReplicationTask.
      mock.awaitResource(IncomingReplicationTask.class, ns, expectedTaskFixture.getName());
      // IncomingReplicationTaskReconciler then creates the CronJob and ConfigMap.
      expectedCronJobFixture.assertEqualTo(
          mock.awaitResource(CronJob.class, ns, expectedCronJobFixture.getName()));
      expectedConfigMapFixture.assertEqualTo(
          mock.awaitResource(ConfigMap.class, ns, expectedConfigMapFixture.getName()));
    }
  }

  @Test
  public void caCertMountedWhenPresentInSecret() throws Exception {
    IncomingReplicationTask incomingReplTask = TestFixtures.incomingReplTask().load();

    String testSecretName = "test-secret-with-ca-cert";
    incomingReplTask.getSpec().setSecretRef(testSecretName);

    Secret testSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withName(testSecretName)
            .withNamespace(incomingReplTask.getMetadata().getNamespace())
            .endMetadata()
            .withData(
                Map.of(
                    ".netrc", "c2VjcmV0Cg==",
                    "ca.crt", "Y2VydGlmaWNhdGUK"))
            .build();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new IncomingReplicationTaskReconciler())) {
      mock.create(testSecret);
      mock.create(incomingReplTask);
      String ns = incomingReplTask.getMetadata().getNamespace();
      CronJob cronJob =
          mock.awaitResource(CronJob.class, ns, incomingReplTask.getMetadata().getName());

      boolean hasCaCertMount =
          cronJob
              .getSpec()
              .getJobTemplate()
              .getSpec()
              .getTemplate()
              .getSpec()
              .getContainers()
              .get(0)
              .getVolumeMounts()
              .stream()
              .anyMatch(
                  vm ->
                      vm.getMountPath().equals("/home/gerrit/ca.crt")
                          && vm.getSubPath().equals("ca.crt"));

      boolean hasGitSslCaInfo =
          cronJob
              .getSpec()
              .getJobTemplate()
              .getSpec()
              .getTemplate()
              .getSpec()
              .getContainers()
              .get(0)
              .getEnv()
              .stream()
              .anyMatch(
                  env ->
                      env.getName().equals("GIT_SSL_CAINFO")
                          && env.getValue().equals("/home/gerrit/ca.crt"));

      assertThat(hasGitSslCaInfo).isTrue();
      assertThat(hasCaCertMount).isTrue();
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.incomingReplGerritCluster(),
            TestFixtures.incomingReplTask(),
            TestFixtures.incomingReplCronJob(),
            TestFixtures.incomingReplConfigMap()));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
