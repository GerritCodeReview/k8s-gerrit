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

package com.google.gerrit.k8s.operator.test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterSpec;
import com.google.gerrit.k8s.operator.cluster.GerritRepositoryConfig;
import com.google.gerrit.k8s.operator.cluster.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.cluster.SharedStorage;
import com.google.gerrit.k8s.operator.cluster.StorageClassConfig;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Util {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String CLUSTER_NAME = "test-cluster";
  public static final String IMAGE_PULL_SECRET_NAME = "image-pull-secret";
  public static final TestProperties testProps = new TestProperties();

  public static KubernetesClient getKubernetesClient() {
    Config config;
    try {
      String kubeconfig = System.getenv("KUBECONFIG");
      if (kubeconfig != null) {
        config = Config.fromKubeconfig(Files.readString(Path.of(kubeconfig)));
        return new DefaultKubernetesClient(config);
      }
      logger.atWarning().log("KUBECONFIG variable not set. Using default config.");
    } catch (IOException e) {
      logger.atSevere().log("Failed to load kubeconfig. Trying default", e);
    }
    return new DefaultKubernetesClient();
  }

  public static GerritCluster createCluster(KubernetesClient client, String namespace) {
    return createCluster(client, namespace, false, false);
  }

  public static GerritCluster createCluster(
      KubernetesClient client, String namespace, boolean isIngressEnabled, boolean isNfsEnbaled) {
    GerritCluster cluster = new GerritCluster();

    cluster.setMetadata(
        new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(namespace).build());

    SharedStorage repoStorage = new SharedStorage();
    repoStorage.setSize(Quantity.parse("1Gi"));

    SharedStorage logStorage = new SharedStorage();
    logStorage.setSize(Quantity.parse("1Gi"));

    StorageClassConfig storageClassConfig = new StorageClassConfig();
    storageClassConfig.setReadWriteMany(testProps.getRWMStorageClass());

    NfsWorkaroundConfig nfsWorkaround = new NfsWorkaroundConfig();
    nfsWorkaround.setEnabled(isNfsEnbaled);
    nfsWorkaround.setIdmapdConfig("[General]\nDomain = localdomain.com");
    storageClassConfig.setNfsWorkaround(nfsWorkaround);

    GerritClusterSpec clusterSpec = new GerritClusterSpec();
    clusterSpec.setGitRepositoryStorage(repoStorage);
    clusterSpec.setLogsStorage(logStorage);
    clusterSpec.setStorageClasses(storageClassConfig);

    GerritRepositoryConfig repoConfig = new GerritRepositoryConfig();
    repoConfig.setOrg(testProps.getRegistryOrg());
    repoConfig.setRegistry(testProps.getRegistry());
    repoConfig.setTag(testProps.getTag());
    clusterSpec.setGerritImages(repoConfig);
    Set<LocalObjectReference> imagePullSecrets = new HashSet<>();
    imagePullSecrets.add(new LocalObjectReference(IMAGE_PULL_SECRET_NAME));
    clusterSpec.setImagePullSecrets(imagePullSecrets);

    cluster.setSpec(clusterSpec);
    client.resources(GerritCluster.class).inNamespace(namespace).createOrReplace(cluster);

    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              GerritCluster updatedCluster =
                  client
                      .resources(GerritCluster.class)
                      .inNamespace(namespace)
                      .withName(CLUSTER_NAME)
                      .get();
              assertThat(updatedCluster, is(notNullValue()));
            });
    return cluster;
  }

  public static void createImagePullSecret(KubernetesClient client, String namespace) {
    StringBuilder secretBuilder = new StringBuilder();
    secretBuilder.append("{\"auths\": {\"");
    secretBuilder.append(testProps.getRegistry());
    secretBuilder.append("\": {\"auth\": \"");
    secretBuilder.append(
        Base64.getEncoder()
            .encodeToString(
                String.format("%s:%s", testProps.getRegistryUser(), testProps.getRegistryPwd())
                    .getBytes()));
    secretBuilder.append("\"}}}");
    String data = Base64.getEncoder().encodeToString(secretBuilder.toString().getBytes());

    Secret imagePullSecret =
        new SecretBuilder()
            .withType("kubernetes.io/dockerconfigjson")
            .withNewMetadata()
            .withName(IMAGE_PULL_SECRET_NAME)
            .withNamespace(namespace)
            .endMetadata()
            .withData(Map.of(".dockerconfigjson", data))
            .build();
    client.secrets().create(imagePullSecret);
  }
}
