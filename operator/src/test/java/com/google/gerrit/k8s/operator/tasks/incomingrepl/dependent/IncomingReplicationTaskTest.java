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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedIncomingReplicationTask;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
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
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedIncomingReplicationTaskComponentsCreated(
      String inputGerritCluster, String expectedIncomingReplTask, String expectedCronJob)
      throws Exception {
    GerritCluster input =
        ReconcilerUtils.loadYaml(GerritCluster.class, this.getClass(), inputGerritCluster);
    IncomingReplicationTask expectedReplTask =
        ReconcilerUtils.loadYaml(
            IncomingReplicationTask.class, this.getClass(), expectedIncomingReplTask);
    IncomingReplicationTask incomingReplTask =
        new ClusterManagedIncomingReplicationTask()
            .desiredResources(input, null)
            .get(expectedReplTask.getMetadata().getName());
    assertThat(incomingReplTask).isEqualTo(expectedReplTask);

    Secret testSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withName(expectedReplTask.getSpec().getSecretRef())
            .withNamespace(expectedReplTask.getMetadata().getNamespace())
            .endMetadata()
            .withData(Map.of(".netrc", "c2VjcmV0Cg=="))
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath(
            String.format(
                "/api/v1/namespaces/%s/secrets/%s",
                incomingReplTask.getMetadata().getNamespace(),
                incomingReplTask.getSpec().getSecretRef()))
        .andReturn(HttpURLConnection.HTTP_OK, testSecret)
        .always();

    IncomingReplicationTaskCronJob incomingReplicationTaskCronJob =
        new IncomingReplicationTaskCronJob();
    incomingReplicationTaskCronJob.setKubernetesClient(kubernetesServer.getClient());
    CronJob cronJob = incomingReplicationTaskCronJob.desired(incomingReplTask, null);

    assertThat(cronJob)
        .isEqualTo(ReconcilerUtils.loadYaml(CronJob.class, this.getClass(), expectedCronJob));
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../gerritcluster_incomingrepl.yaml",
            "../incomingrepltask.yaml",
            "incomingrepl_cronjob.yaml"));
  }
}
