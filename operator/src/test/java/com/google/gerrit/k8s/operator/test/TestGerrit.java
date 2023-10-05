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

import static com.google.gerrit.k8s.operator.test.TestSecureConfig.SECURE_CONFIG_SECRET_NAME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritInitConfigMap;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritSite;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplateSpec;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.GerritRepositoryConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.GerritStorageConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.IngressConfig;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.SharedStorage;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.StorageClassConfig;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class TestGerrit {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final TestProperties testProps = new TestProperties();
  public static final String DEFAULT_GERRIT_CONFIG =
      "[index]\n"
          + "  type = LUCENE\n"
          + "[auth]\n"
          + "  type = LDAP\n"
          + "[ldap]\n"
          + "  server = ldap://openldap.openldap.svc.cluster.local:1389\n"
          + "  accountBase = dc=example,dc=org\n"
          + "  username = cn=admin,dc=example,dc=org\n"
          + "[httpd]\n"
          + "  requestLog = true\n"
          + "  gracefulStopTimeout = 1m\n"
          + "[transfer]\n"
          + "  timeout = 120 s\n"
          + "[user]\n"
          + "  name = Gerrit Code Review\n"
          + "  email = gerrit@example.com\n"
          + "  anonymousCoward = Unnamed User\n"
          + "[container]\n"
          + "  javaOptions = -Xmx4g";

  private final KubernetesClient client;
  private final String name;
  private final String namespace;
  private final GerritMode mode;

  private Gerrit gerrit = new Gerrit();
  private Config config = defaultConfig();

  public TestGerrit(
      KubernetesClient client,
      TestProperties testProps,
      GerritMode mode,
      String name,
      String namespace) {
    this.client = client;
    this.mode = mode;
    this.name = name;
    this.namespace = namespace;
  }

  public TestGerrit(
      KubernetesClient client, TestProperties testProps, String name, String namespace) {
    this(client, testProps, GerritMode.PRIMARY, name, namespace);
  }

  public void build() {
    createGerritCR();
  }

  public void deploy() {
    build();
    client.resource(gerrit).inNamespace(namespace).createOrReplace();
    waitForGerritReadiness();
  }

  public void modifyGerritConfig(String section, String key, String value) {
    config.setString(section, null, key, value);
  }

  public GerritSpec getSpec() {
    return gerrit.getSpec();
  }

  public void setSpec(GerritSpec spec) {
    gerrit.setSpec(spec);
    deploy();
  }

  private static Config defaultConfig() {
    Config cfg = new Config();
    try {
      cfg.fromText(DEFAULT_GERRIT_CONFIG);
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("Illegal default test configuration.");
    }
    return cfg;
  }

  public GerritTemplate createGerritTemplate() throws ConfigInvalidException {
    return createGerritTemplate(name, mode, config);
  }

  public static GerritTemplate createGerritTemplate(String name, GerritMode mode)
      throws ConfigInvalidException {
    Config cfg = new Config();
    cfg.fromText(DEFAULT_GERRIT_CONFIG);
    return createGerritTemplate(name, mode, cfg);
  }

  public static GerritTemplate createGerritTemplate(String name, GerritMode mode, Config config) {
    GerritTemplate template = new GerritTemplate();
    ObjectMeta gerritMeta = new ObjectMetaBuilder().withName(name).build();
    template.setMetadata(gerritMeta);
    GerritTemplateSpec gerritSpec = template.getSpec();
    if (gerritSpec == null) {
      gerritSpec = new GerritTemplateSpec();
      GerritSite site = new GerritSite();
      site.setSize(new Quantity("1Gi"));
      gerritSpec.setSite(site);
      gerritSpec.setResources(
          new ResourceRequirementsBuilder()
              .withRequests(Map.of("cpu", new Quantity("1"), "memory", new Quantity("5Gi")))
              .build());
    }
    gerritSpec.setMode(mode);
    gerritSpec.setConfigFiles(Map.of("gerrit.config", config.toText()));
    gerritSpec.setSecretRef(SECURE_CONFIG_SECRET_NAME);
    template.setSpec(gerritSpec);
    return template;
  }

  private void createGerritCR() {
    ObjectMeta gerritMeta = new ObjectMetaBuilder().withName(name).withNamespace(namespace).build();
    gerrit.setMetadata(gerritMeta);
    GerritSpec gerritSpec = gerrit.getSpec();
    if (gerritSpec == null) {
      gerritSpec = new GerritSpec();
      GerritSite site = new GerritSite();
      site.setSize(new Quantity("1Gi"));
      gerritSpec.setSite(site);
      gerritSpec.setServerId("gerrit-1234");
      gerritSpec.setResources(
          new ResourceRequirementsBuilder()
              .withRequests(Map.of("cpu", new Quantity("1"), "memory", new Quantity("5Gi")))
              .build());
    }
    gerritSpec.setMode(mode);
    gerritSpec.setConfigFiles(Map.of("gerrit.config", config.toText()));
    gerritSpec.setSecretRef(SECURE_CONFIG_SECRET_NAME);

    SharedStorage sharedStorage = new SharedStorage();
    sharedStorage.setSize(Quantity.parse("1Gi"));

    StorageClassConfig storageClassConfig = new StorageClassConfig();
    storageClassConfig.setReadWriteMany(testProps.getRWMStorageClass());

    GerritStorageConfig gerritStorageConfig = new GerritStorageConfig();
    gerritStorageConfig.setSharedStorage(sharedStorage);
    gerritStorageConfig.setStorageClasses(storageClassConfig);
    gerritSpec.setStorage(gerritStorageConfig);

    GerritRepositoryConfig repoConfig = new GerritRepositoryConfig();
    repoConfig.setOrg(testProps.getRegistryOrg());
    repoConfig.setRegistry(testProps.getRegistry());
    repoConfig.setTag(testProps.getTag());

    ContainerImageConfig containerImageConfig = new ContainerImageConfig();
    containerImageConfig.setGerritImages(repoConfig);
    Set<LocalObjectReference> imagePullSecrets = new HashSet<>();
    imagePullSecrets.add(
        new LocalObjectReference(AbstractGerritOperatorE2ETest.IMAGE_PULL_SECRET_NAME));
    containerImageConfig.setImagePullSecrets(imagePullSecrets);
    gerritSpec.setContainerImages(containerImageConfig);

    IngressConfig ingressConfig = new IngressConfig();
    ingressConfig.setHost(testProps.getIngressDomain());
    ingressConfig.setTlsEnabled(false);
    gerritSpec.setIngress(ingressConfig);

    gerrit.setSpec(gerritSpec);
  }

  private void waitForGerritReadiness() {
    logger.atInfo().log("Waiting max 1 minutes for the configmaps to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .configMaps()
                      .inNamespace(namespace)
                      .withName(GerritConfigMap.getName(gerrit))
                      .get(),
                  is(notNullValue()));
              assertThat(
                  client
                      .configMaps()
                      .inNamespace(namespace)
                      .withName(GerritInitConfigMap.getName(gerrit))
                      .get(),
                  is(notNullValue()));
            });

    logger.atInfo().log("Waiting max 1 minutes for the Gerrit StatefulSet to be created.");
    await()
        .atMost(1, MINUTES)
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
            });

    logger.atInfo().log("Waiting max 1 minutes for the Gerrit Service to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .services()
                      .inNamespace(namespace)
                      .withName(GerritService.getName(gerrit))
                      .get(),
                  is(notNullValue()));
            });

    logger.atInfo().log("Waiting max 2 minutes for the Gerrit StatefulSet to be ready.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertTrue(
                  client
                      .apps()
                      .statefulSets()
                      .inNamespace(namespace)
                      .withName(gerrit.getMetadata().getName())
                      .isReady());
            });
  }
}
