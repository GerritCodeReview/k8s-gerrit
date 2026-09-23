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
import static com.google.gerrit.k8s.operator.test.KubernetesResourceAssert.assertUnordered;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedIncomingReplicationTask;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.IncomingReplicationTaskReconciler;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GenericRetryExecution;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class IncomingReplicationTaskTest {
  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    kubernetesServer.before();
    OperatorContext.createInstance(ClusterMode.HIGH_AVAILABILITY, "cluster.local");
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedIncomingReplicationTaskComponentsCreated(
      KubernetesResourceFixture<GerritCluster> inputFixture,
      KubernetesResourceFixture<IncomingReplicationTask> expectedTaskFixture,
      KubernetesResourceFixture<CronJob> expectedCronJobFixture,
      KubernetesResourceFixture<ConfigMap> expectedConfigMapFixture)
      throws Exception {
    GerritCluster input = inputFixture.load();
    IncomingReplicationTask expectedReplTask = expectedTaskFixture.load();

    IncomingReplicationTask incomingReplTask =
        new ClusterManagedIncomingReplicationTask()
            .desiredResources(input, null)
            .get(expectedReplTask.getMetadata().getName());
    assertThat(incomingReplTask).isEqualTo(expectedReplTask);

    stubSecret(
        incomingReplTask.getSpec().getSecretRef(),
        incomingReplTask.getMetadata().getNamespace(),
        Map.of(".netrc", "c2VjcmV0Cg=="));

    Context<IncomingReplicationTask> context =
        getContext(new IncomingReplicationTaskReconciler(), incomingReplTask);

    assertUnordered(
        new IncomingReplicationTaskCronJob().desired(incomingReplTask, context),
        expectedCronJobFixture.load());
    assertUnordered(
        new IncomingReplicationTaskConfigMap().desired(incomingReplTask, context),
        expectedConfigMapFixture.load());
  }

  @Test
  public void caCertMountedWhenPresentInSecret() throws Exception {
    IncomingReplicationTask incomingReplTask = TestFixtures.incomingReplTask().load();

    String testSecretName = "test-secret-with-ca-cert";
    incomingReplTask.getSpec().setSecretRef(testSecretName);

    stubSecret(
        testSecretName,
        incomingReplTask.getMetadata().getNamespace(),
        Map.of(".netrc", "c2VjcmV0Cg==", "ca.crt", "Y2VydGlmaWNhdGUK"));

    Context<IncomingReplicationTask> context =
        getContext(new IncomingReplicationTaskReconciler(), incomingReplTask);
    CronJob cronJob = new IncomingReplicationTaskCronJob().desired(incomingReplTask, context);

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

    assertThat(hasCaCertMount).isTrue();
    assertThat(hasGitSslCaInfo).isTrue();
  }

  private void stubSecret(String name, String namespace, Map<String, String> data) {
    Secret secret =
        new SecretBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .endMetadata()
            .withData(data)
            .build();
    kubernetesServer
        .expect()
        .get()
        .withPath(String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name))
        .andReturn(HttpURLConnection.HTTP_OK, secret)
        .always();
  }

  private Context<IncomingReplicationTask> getContext(
      Reconciler<IncomingReplicationTask> reconciler, IncomingReplicationTask primary) {
    Controller<IncomingReplicationTask> controller =
        new Controller<>(
            reconciler,
            new BaseConfigurationService().getConfigurationFor(reconciler),
            kubernetesServer.getClient());
    return new DefaultContext<>(new GenericRetryExecution(new GenericRetry()), controller, primary);
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.incomingReplGerritCluster(),
            TestFixtures.incomingReplTask(),
            TestFixtures.incomingReplCronJob(),
            TestFixtures.incomingReplConfigMap()));
  }
}
