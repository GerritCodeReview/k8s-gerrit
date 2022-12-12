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

import static com.google.gerrit.k8s.operator.test.TestGerritCluster.CLUSTER_NAME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritConfigMapDependentResource;
import com.google.gerrit.k8s.operator.gerrit.GerritInitConfigMapDependentResource;
import com.google.gerrit.k8s.operator.gerrit.GerritSite;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec.GerritMode;
import com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class TestGerrit {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String NAME = "gerrit";
  private static final String SECURE_CONFIG_SECRET_NAME = "gerrit-secret";
  private static final String DEFAULT_GERRIT_CONFIG =
      "[gerrit]\n"
          + "  serverId = gerrit-1\n"
          + "[index]\n"
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
  private final String namespace;
  private final GerritMode mode;
  private final String ingress_domain;

  private Secret secureConfigSecret;
  private Gerrit gerrit = new Gerrit();
  private Config config = defaultConfig();
  private Config secureConfig = new Config();

  public TestGerrit(
      KubernetesClient client,
      TestProperties testProps,
      TestGerritCluster cluster,
      GerritMode mode) {
    this.client = client;
    this.namespace = cluster.getNamespace();
    this.mode = mode;
    this.ingress_domain = cluster.getHostname();
    this.secureConfig.setString("ldap", null, "password", testProps.getLdapAdminPwd());
  }

  public TestGerrit(KubernetesClient client, TestProperties testProps, TestGerritCluster cluster) {
    this(client, testProps, cluster, GerritMode.PRIMARY);
  }

  public void build() {
    createGerritCR();
    createSecureConfig();
  }

  public void deploy() {
    build();
    client.resource(secureConfigSecret).inNamespace(namespace).createOrReplace();
    client.resource(gerrit).inNamespace(namespace).createOrReplace();
    waitForGerritReadiness();
  }

  public GerritApi getGerritApiClient() {
    return new GerritRestApiFactory()
        .create(
            new GerritAuthData.Basic(
                String.format(
                    "https://%s.%s", ServiceDependentResource.getName(gerrit), ingress_domain)));
  }

  public void modifyGerritConfig(String section, String key, String value) {
    config.setString(section, null, key, value);
    deploy();
  }

  public void modifySecureConfig(String section, String key, String value) {
    secureConfig.setString(section, null, key, value);
    createSecureConfig();
    client.resource(secureConfigSecret).inNamespace(namespace).createOrReplace();
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

  private void createGerritCR() {
    ObjectMeta gerritMeta = new ObjectMetaBuilder().withName(NAME).withNamespace(namespace).build();
    gerrit.setMetadata(gerritMeta);
    GerritSpec gerritSpec = gerrit.getSpec();
    if (gerritSpec == null) {
      gerritSpec = new GerritSpec();
      GerritSite site = new GerritSite();
      site.setSize(new Quantity("1Gi"));
      gerritSpec.setSite(site);
      gerritSpec.setResources(
          new ResourceRequirementsBuilder()
              .withRequests(Map.of("cpu", new Quantity("1"), "memory", new Quantity("5Gi")))
              .build());
    }
    gerritSpec.setCluster(CLUSTER_NAME);
    gerritSpec.setMode(mode);
    gerritSpec.setConfigFiles(Map.of("gerrit.config", config.toText()));
    gerritSpec.setSecrets(Set.of(SECURE_CONFIG_SECRET_NAME));

    gerrit.setSpec(gerritSpec);
  }

  private void createSecureConfig() {
    secureConfigSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(SECURE_CONFIG_SECRET_NAME)
            .endMetadata()
            .withData(
                Map.of(
                    "secure.config",
                    Base64.getEncoder().encodeToString(secureConfig.toText().getBytes())))
            .build();
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
                      .withName(GerritConfigMapDependentResource.getName(gerrit))
                      .get(),
                  is(notNullValue()));
              assertThat(
                  client
                      .configMaps()
                      .inNamespace(namespace)
                      .withName(GerritInitConfigMapDependentResource.getName(gerrit))
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
                      .withName(ServiceDependentResource.getName(gerrit))
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
