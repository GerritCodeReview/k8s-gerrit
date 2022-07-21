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

package com.google.cgerrit.k8s.operator.gerrit;

import static com.google.gerrit.k8s.operator.gerrit.ServiceDependentResource.GERRIT_SERVICE_NAME;
import static com.google.gerrit.k8s.operator.test.Util.createCluster;
import static com.google.gerrit.k8s.operator.test.Util.createImagePullSecret;
import static com.google.gerrit.k8s.operator.test.Util.getKubernetesClient;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritConfigMapDependentResource;
import com.google.gerrit.k8s.operator.gerrit.GerritInitConfigMapDependentResource;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gerrit.GerritSite;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GerritE2E {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final KubernetesClient client = getKubernetesClient();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .waitForNamespaceDeletion(true)
          .withReconciler(new GerritClusterReconciler(client))
          .withReconciler(new GerritReconciler())
          .build();

  @BeforeEach
  void setup() {
    createImagePullSecret(client, operator.getNamespace());
  }

  @Test
  void testGerritStatefulSetCreated() {
    GerritCluster cluster = createCluster(client, operator.getNamespace());

    Gerrit gerrit = new Gerrit();
    ObjectMeta gerritMeta =
        new ObjectMetaBuilder().withName("gerrit").withNamespace(operator.getNamespace()).build();
    gerrit.setMetadata(gerritMeta);
    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setCluster(cluster.getMetadata().getName());
    GerritSite site = new GerritSite();
    site.setSize(new Quantity("1Gi"));
    gerritSpec.setSite(site);
    gerritSpec.setResources(
        new ResourceRequirementsBuilder()
            .withRequests(Map.of("cpu", new Quantity("1"), "memory", new Quantity("5Gi")))
            .build());
    gerritSpec.setConfigFiles(
        Map.of(
            "gerrit.config",
            "        [gerrit]\n"
                + "          basePath = git\n"
                + "          serverId = gerrit-1\n"
                + "          canonicalWebUrl = http://example.com/\n"
                + "        [index]\n"
                + "          type = LUCENE\n"
                + "          onlineUpgrade = false\n"
                + "        [auth]\n"
                + "          type = DEVELOPMENT_BECOME_ANY_ACCOUNT\n"
                + "        [httpd]\n"
                + "          listenUrl = proxy-http://*:8080/\n"
                + "          requestLog = true\n"
                + "          gracefulStopTimeout = 1m\n"
                + "        [sshd]\n"
                + "          listenAddress = off\n"
                + "        [transfer]\n"
                + "          timeout = 120 s\n"
                + "        [user]\n"
                + "          name = Gerrit Code Review\n"
                + "          email = gerrit@example.com\n"
                + "          anonymousCoward = Unnamed User\n"
                + "        [cache]\n"
                + "          directory = cache\n"
                + "        [container]\n"
                + "          user = gerrit\n"
                + "          javaHome = /usr/lib/jvm/java-11-openjdk\n"
                + "          javaOptions = -Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore\n"
                + "          javaOptions = -Xmx4g"));

    gerrit.setSpec(gerritSpec);
    client.resource(gerrit).createOrReplace();

    logger.atInfo().log("Waiting max 1 minutes for the configmaps to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .withName(GerritConfigMapDependentResource.GERRIT_CONFIGMAP_NAME)
                      .get(),
                  is(notNullValue()));
              assertThat(
                  client
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .withName(GerritInitConfigMapDependentResource.GERRIT_INIT_CONFIGMAP_NAME)
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
                      .inNamespace(operator.getNamespace())
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
                      .inNamespace(operator.getNamespace())
                      .withName(GERRIT_SERVICE_NAME)
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
                      .inNamespace(operator.getNamespace())
                      .withName(gerrit.getMetadata().getName())
                      .isReady());
            });
  }

  @AfterEach
  void cleanup() {
    client.resources(Gerrit.class).inNamespace(operator.getNamespace()).delete();
    client.resources(GerritCluster.class).inNamespace(operator.getNamespace()).delete();
  }
}
