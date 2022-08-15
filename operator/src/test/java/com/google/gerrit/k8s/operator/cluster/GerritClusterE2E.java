// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.cluster;

import static com.google.gerrit.k8s.operator.test.Util.createCluster;
import static com.google.gerrit.k8s.operator.test.Util.getKubernetesClient;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionListReconciler;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionReconciler;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GerritClusterE2E {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final KubernetesClient client = getKubernetesClient();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .waitForNamespaceDeletion(true)
          .withReconciler(new GerritClusterReconciler(client))
          .withReconciler(new GitGarbageCollectionListReconciler(client))
          .withReconciler(new GitGarbageCollectionReconciler(client))
          .build();

  @Test
  void testGitRepositoriesPvcCreated() {
    GerritCluster cluster = createCluster(client, operator.getNamespace());

    logger.atInfo().log("Waiting max 1 minutes for the git repositories pvc to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              PersistentVolumeClaim pvc =
                  client
                      .persistentVolumeClaims()
                      .inNamespace(operator.getNamespace())
                      .withName(GitRepositoriesPVC.REPOSITORY_PVC_NAME)
                      .get();
              assertThat(pvc, is(notNullValue()));
            });

    logger.atInfo().log("Deleting test cluster object: %s", cluster);
    client.resource(cluster).delete();
  }

  @Test
  void testGerritLogsPvcCreated() {
    GerritCluster cluster = createCluster(client, operator.getNamespace());

    logger.atInfo().log("Waiting max 1 minutes for the gerrit logs pvc to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              PersistentVolumeClaim pvc =
                  client
                      .persistentVolumeClaims()
                      .inNamespace(operator.getNamespace())
                      .withName(GerritLogsPVC.LOGS_PVC_NAME)
                      .get();
              assertThat(pvc, is(notNullValue()));
            });

    logger.atInfo().log("Deleting test cluster object: %s", cluster);
    client.resource(cluster).delete();
  }

  @Test
  void testNfsIdmapdConfigMapCreated() {
    GerritCluster cluster = createCluster(client, operator.getNamespace(), false, true);

    logger.atInfo().log("Waiting max 1 minutes for the nfs idmapd configmap to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              ConfigMap cm =
                  client
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .withName(NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME)
                      .get();
              assertThat(cm, is(notNullValue()));
            });

    logger.atInfo().log("Deleting test cluster object: %s", cluster);
  }

  @AfterEach
  void cleanup() {
    client.resources(GerritCluster.class).inNamespace(operator.getNamespace()).delete();
  }
}
