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

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.model.GerritClusterSpec;
import com.google.gerrit.k8s.operator.cluster.model.GerritIngressConfig;
import com.google.gerrit.k8s.operator.cluster.model.GerritIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.cluster.model.GerritIngressTlsConfig;
import com.google.gerrit.k8s.operator.cluster.model.GerritRepositoryConfig;
import com.google.gerrit.k8s.operator.cluster.model.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.cluster.model.SharedStorage;
import com.google.gerrit.k8s.operator.cluster.model.StorageClassConfig;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestGerritCluster {
  public static final String CLUSTER_NAME = "test-cluster";
  public static final TestProperties testProps = new TestProperties();

  private final KubernetesClient client;
  private final String namespace;

  private GerritIngressConfig ingressConfig;
  private boolean isNfsEnabled = false;
  private GerritCluster cluster = new GerritCluster();
  private String hostname;

  public TestGerritCluster(KubernetesClient client, String namespace) {
    this.client = client;
    this.namespace = namespace;

    defaultIngressConfig();
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
        enableIngress();
        break;
      case ISTIO:
        enableIstio();
        break;
      default:
        defaultIngressConfig();
    }
    deploy();
  }

  private void defaultIngressConfig() {
    hostname = null;
    ingressConfig = new GerritIngressConfig();
    ingressConfig.setEnabled(false);
  }

  private void enableIngress() {
    hostname = testProps.getIngressDomain();
    ingressConfig = new GerritIngressConfig();
    ingressConfig.setEnabled(true);
    ingressConfig.setType(IngressType.INGRESS);
    ingressConfig.setHost(hostname);
    ingressConfig.setAnnotations(Map.of("kubernetes.io/ingress.class", "nginx"));
    GerritIngressTlsConfig ingressTlsConfig = new GerritIngressTlsConfig();
    ingressTlsConfig.setEnabled(true);
    ingressTlsConfig.setSecret("tls-secret");
    ingressConfig.setTls(ingressTlsConfig);
  }

  private void enableIstio() {
    hostname = testProps.getIstioDomain();
    ingressConfig = new GerritIngressConfig();
    ingressConfig.setEnabled(true);
    ingressConfig.setType(IngressType.ISTIO);
    ingressConfig.setHost(hostname);
    ingressConfig.setAnnotations(Map.of("kubernetes.io/ingress.class", "nginx"));
    GerritIngressTlsConfig ingressTlsConfig = new GerritIngressTlsConfig();
    ingressTlsConfig.setEnabled(true);
    ingressTlsConfig.setSecret("tls-secret");
    ingressConfig.setTls(ingressTlsConfig);
  }

  public void setNfsEnabled(boolean isNfsEnabled) {
    this.isNfsEnabled = isNfsEnabled;
    deploy();
  }

  private void build() {
    cluster.setMetadata(
        new ObjectMetaBuilder().withName(CLUSTER_NAME).withNamespace(namespace).build());

    SharedStorage repoStorage = new SharedStorage();
    repoStorage.setSize(Quantity.parse("1Gi"));

    SharedStorage logStorage = new SharedStorage();
    logStorage.setSize(Quantity.parse("1Gi"));

    StorageClassConfig storageClassConfig = new StorageClassConfig();
    storageClassConfig.setReadWriteMany(testProps.getRWMStorageClass());

    NfsWorkaroundConfig nfsWorkaround = new NfsWorkaroundConfig();
    nfsWorkaround.setEnabled(isNfsEnabled);
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
    imagePullSecrets.add(
        new LocalObjectReference(AbstractGerritOperatorE2ETest.IMAGE_PULL_SECRET_NAME));
    clusterSpec.setImagePullSecrets(imagePullSecrets);
    clusterSpec.setIngress(ingressConfig);

    cluster.setSpec(clusterSpec);
  }

  public void deploy() {
    build();
    client.resource(cluster).inNamespace(namespace).createOrReplace();

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
  }
}
