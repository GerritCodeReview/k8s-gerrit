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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritClusterIngressConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritIngressTlsConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritRepositoryConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.api.model.shared.SharedStorage;
import com.google.gerrit.k8s.operator.api.model.shared.StorageClassConfig;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TestGerritCluster {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String CLUSTER_NAME = "test-cluster";
  public static final TestProperties testProps = new TestProperties();

  private final KubernetesClient client;
  private final String namespace;

  private GerritClusterIngressConfig ingressConfig;
  private boolean isNfsEnabled = false;
  private GerritCluster cluster = new GerritCluster();
  private String hostname;
  private List<GerritTemplate> gerrits = new ArrayList<>();
  private Optional<ReceiverTemplate> receiver = Optional.empty();

  public TestGerritCluster(KubernetesClient client, String namespace) {
    this.client = client;
    this.namespace = namespace;

    defaultIngressConfig();
  }

  public GerritCluster getGerritCluster() {
    return cluster;
  }

  public String getHostname() {
    return hostname;
  }

  public String getNamespace() {
    return cluster.getMetadata().getNamespace();
  }

  public void setIngressType(IngressType type) {
    switch (type) {
      case INGRESS:
        hostname = testProps.getIngressDomain();
        enableIngress();
        break;
      case ISTIO:
        hostname = testProps.getIstioDomain();
        enableIngress();
        break;
      default:
        hostname = null;
        defaultIngressConfig();
    }
    deploy();
  }

  private void defaultIngressConfig() {
    ingressConfig = new GerritClusterIngressConfig();
    ingressConfig.setEnabled(false);
  }

  private void enableIngress() {
    ingressConfig = new GerritClusterIngressConfig();
    ingressConfig.setEnabled(true);
    ingressConfig.setHost(hostname);
    GerritIngressTlsConfig ingressTlsConfig = new GerritIngressTlsConfig();
    ingressTlsConfig.setEnabled(true);
    ingressTlsConfig.setSecret("tls-secret");
    ingressConfig.setTls(ingressTlsConfig);
  }

  public GerritApi getGerritApiClient(GerritTemplate gerrit, IngressType ingressType) {
    return new GerritRestApiFactory()
        .create(new GerritAuthData.Basic(String.format("https://%s", hostname)));
  }

  public void setNfsEnabled(boolean isNfsEnabled) {
    this.isNfsEnabled = isNfsEnabled;
    deploy();
  }

  public void addGerrit(GerritTemplate gerrit) {
    gerrits.add(gerrit);
  }

  public void removeGerrit(GerritTemplate gerrit) {
    gerrits.remove(gerrit);
  }

  public void setReceiver(ReceiverTemplate receiver) {
    this.receiver = Optional.ofNullable(receiver);
  }

  public GerritCluster build() {
    cluster.setMetadata(
        new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(namespace).build());

    SharedStorage sharedStorage = new SharedStorage();
    sharedStorage.setSize(Quantity.parse("1Gi"));

    StorageClassConfig storageClassConfig = new StorageClassConfig();
    storageClassConfig.setReadWriteMany(testProps.getRWMStorageClass());

    NfsWorkaroundConfig nfsWorkaround = new NfsWorkaroundConfig();
    nfsWorkaround.setEnabled(isNfsEnabled);
    nfsWorkaround.setIdmapdConfig("[General]\nDomain = localdomain.com");
    storageClassConfig.setNfsWorkaround(nfsWorkaround);

    GerritClusterSpec clusterSpec = new GerritClusterSpec();
    GerritStorageConfig gerritStorageConfig = new GerritStorageConfig();
    gerritStorageConfig.setSharedStorage(sharedStorage);
    gerritStorageConfig.setStorageClasses(storageClassConfig);
    clusterSpec.setStorage(gerritStorageConfig);

    GerritRepositoryConfig repoConfig = new GerritRepositoryConfig();
    repoConfig.setOrg(testProps.getRegistryOrg());
    repoConfig.setRegistry(testProps.getRegistry());
    repoConfig.setTag(testProps.getTag());

    ContainerImageConfig containerImageConfig = new ContainerImageConfig();
    containerImageConfig.setGerritImages(repoConfig);
    Set<LocalObjectReference> imagePullSecrets = new HashSet<>();
    imagePullSecrets.add(
        new LocalObjectReference(AbstractGerritOperatorE2EBase.IMAGE_PULL_SECRET_NAME));
    containerImageConfig.setImagePullSecrets(imagePullSecrets);
    clusterSpec.setContainerImages(containerImageConfig);

    clusterSpec.setIngress(ingressConfig);

    clusterSpec.setGerrits(gerrits);
    if (receiver.isPresent()) {
      clusterSpec.setReceiver(receiver.get());
    }

    cluster.setSpec(clusterSpec);
    return cluster;
  }

  public void deploy() {
    build();
    client.resource(cluster).inNamespace(namespace).createOrReplace();
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .resources(GerritCluster.class)
                      .inNamespace(namespace)
                      .withName(CLUSTER_NAME)
                      .get(),
                  is(notNullValue()));
            });

    GerritCluster updatedCluster =
        client.resources(GerritCluster.class).inNamespace(namespace).withName(CLUSTER_NAME).get();
    for (GerritTemplate gerrit : updatedCluster.getSpec().getGerrits()) {
      waitForGerritReadiness(gerrit);
    }
    if (receiver.isPresent()) {
      waitForReceiverReadiness();
    }
  }

  private void waitForGerritReadiness(GerritTemplate gerrit) {
    logger.atInfo().log("Waiting max 2 minutes for the Gerrit StatefulSet to be ready.");
    await()
        .pollDelay(15, SECONDS)
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .apps()
                      .statefulSets()
                      .inNamespace(namespace)
                      .withName(gerrit.getMetadata().getName())
                      .get(),
                  is(notNullValue()));
              assertTrue(
                  client
                      .apps()
                      .statefulSets()
                      .inNamespace(namespace)
                      .withName(gerrit.getMetadata().getName())
                      .isReady());
              assertTrue(
                  client
                      .pods()
                      .inNamespace(namespace)
                      .withName(gerrit.getMetadata().getName() + "-0")
                      .isReady());
            });
  }

  private void waitForReceiverReadiness() {
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertTrue(
                  client
                      .apps()
                      .deployments()
                      .inNamespace(namespace)
                      .withName(receiver.get().getMetadata().getName())
                      .isReady());
            });
  }
}
