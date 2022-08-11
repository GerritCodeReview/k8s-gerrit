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

import static com.google.gerrit.k8s.operator.test.Util.getKubernetesClient;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.flogger.FluentLogger;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GerritClusterE2E {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final KubernetesClient client = getKubernetesClient();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .waitForNamespaceDeletion(true)
          .withReconciler(new GerritClusterReconciler())
          .build();

  @Test
  void testGitRepositoriesPvcCreated() {
    GerritCluster cluster = new GerritCluster();

    cluster.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-cluster")
            .withNamespace(operator.getNamespace())
            .build());

    GitRepositoryStorage repoStorage = new GitRepositoryStorage();
    repoStorage.setSize(Quantity.parse("1Gi"));

    StorageClassConfig storageClassConfig = new StorageClassConfig();
    storageClassConfig.setReadWriteMany(System.getProperty("rwmStorageClass", "nfs-client"));

    GerritClusterSpec clusterSpec = new GerritClusterSpec();
    clusterSpec.setGitRepositoryStorage(repoStorage);
    clusterSpec.setStorageClasses(storageClassConfig);

    cluster.setSpec(clusterSpec);

    client
        .resources(GerritCluster.class)
        .inNamespace(operator.getNamespace())
        .createOrReplace(cluster);

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
}
