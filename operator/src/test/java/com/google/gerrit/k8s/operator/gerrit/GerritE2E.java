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

package com.google.gerrit.k8s.operator.gerrit;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.network.GerritIngressConfig;
import com.google.gerrit.k8s.operator.network.GerritIngressTlsConfig;
import com.google.gerrit.k8s.operator.network.GerritNetwork;
import com.google.gerrit.k8s.operator.network.GerritNetworkSpec;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestProperties;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GerritE2E extends AbstractGerritOperatorE2ETest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String INGRESS_NAME = "gerrit-ingress";
  private static final String INGRESS_DOMAIN = new TestProperties().getIngressDomain();

  @Test
  void testGerritStatefulSetCreated() throws Exception {
    GerritCluster cluster = createCluster(client, operator.getNamespace(), true, false);

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
                      .inNamespace(operator.getNamespace())
                      .withName(gerrit.getMetadata().getName())
                      .isReady());
            });

    GerritNetwork network = new GerritNetwork();
    network.setMetadata(
        new ObjectMetaBuilder()
            .withName(INGRESS_NAME)
            .withNamespace(operator.getNamespace())
            .build());

    GerritNetworkSpec networkSpec = new GerritNetworkSpec();
    networkSpec.setCluster(cluster.getMetadata().getName());

    GerritIngressConfig ingressConfig = new GerritIngressConfig();
    ingressConfig.setHost(INGRESS_DOMAIN);
    ingressConfig.setAnnotations(Map.of("kubernetes.io/ingress.class", "nginx"));
    GerritIngressTlsConfig ingressTlsConfig = new GerritIngressTlsConfig();
    ingressTlsConfig.setEnabled(true);
    ingressTlsConfig.setSecret("tls-secret");
    ingressConfig.setTls(ingressTlsConfig);
    networkSpec.setIngress(ingressConfig);

    network.setSpec(networkSpec);
    client.resource(network).createOrReplace();

    logger.atInfo().log("Waiting max 2 minutes for the Ingress to have an external IP.");
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              Ingress ingress =
                  client
                      .network()
                      .v1()
                      .ingresses()
                      .inNamespace(operator.getNamespace())
                      .withName(INGRESS_NAME)
                      .get();
              assertThat(ingress, is(notNullValue()));
              IngressStatus status = ingress.getStatus();
              assertThat(status, is(notNullValue()));
              List<LoadBalancerIngress> lbIngresses = status.getLoadBalancer().getIngress();
              assertThat(lbIngresses, hasSize(1));
              assertThat(lbIngresses.get(0).getIp(), is(notNullValue()));
            });

    GerritApi gerritApi =
        new GerritRestApiFactory()
            .create(
                new GerritAuthData.Basic(
                    String.format(
                        "http://%s.%s", ServiceDependentResource.getName(gerrit), INGRESS_DOMAIN)));

    logger.atInfo().log("Gerrit version: %s", gerritApi.config().server().getVersion());
    assertThat(gerritApi.config().server().getVersion(), notNullValue());
    assertThat(gerritApi.config().server().getVersion(), not(is("<2.8")));
  }
}
