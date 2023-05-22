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

import static com.google.gerrit.k8s.operator.cluster.dependent.GerritIngress.INGRESS_NAME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.k8s.operator.cluster.model.GerritClusterIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.gerrit.model.GerritServiceConfig;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ClusterManagedGerritE2E extends AbstractGerritOperatorE2ETest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Test
  void testPrimaryGerritIsCreated() throws Exception {
    gerritCluster.setIngressType(IngressType.INGRESS);
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.PRIMARY, "gerrit", operator.getNamespace());
    GerritTemplate gerritTemplate = gerrit.createGerritTemplate();
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();

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
              List<IngressLoadBalancerIngress> lbIngresses = status.getLoadBalancer().getIngress();
              assertThat(lbIngresses, hasSize(1));
              assertThat(lbIngresses.get(0).getIp(), is(notNullValue()));
            });

    GerritApi gerritApi = gerritCluster.getGerritApiClientForIngress(gerritTemplate);
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertDoesNotThrow(() -> gerritApi.config().server().getVersion());
              assertThat(gerritApi.config().server().getVersion(), notNullValue());
              assertThat(gerritApi.config().server().getVersion(), not(is("<2.8")));
              logger.atInfo().log("Gerrit version: %s", gerritApi.config().server().getVersion());
            });
  }

  @Test
  void testPrimaryGerritWithIstio() throws Exception {
    gerritCluster.setIngressType(IngressType.ISTIO);
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.PRIMARY, "gerrit", operator.getNamespace());
    gerritCluster.addGerrit(gerrit.createGerritTemplate());
    gerritCluster.deploy();

    GerritApi gerritApi = gerritCluster.getGerritApiClientForIstio();
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertDoesNotThrow(() -> gerritApi.config().server().getVersion());
              assertThat(gerritApi.config().server().getVersion(), notNullValue());
              assertThat(gerritApi.config().server().getVersion(), not(is("<2.8")));
              logger.atInfo().log("Gerrit version: %s", gerritApi.config().server().getVersion());
            });
  }

  @Test
  void testGerritReplicaIsCreated() throws Exception {
    String gerritName = "gerrit-replica";
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.REPLICA, gerritName, operator.getNamespace());
    gerritCluster.addGerrit(gerrit.createGerritTemplate());
    gerritCluster.deploy();

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(gerritName + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review [replica]"));
  }

  @Test
  void testMultipleGerritReplicaAreCreated() throws Exception {
    String gerritName = "gerrit-replica-1";
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.REPLICA, gerritName, operator.getNamespace());
    gerritCluster.addGerrit(gerrit.createGerritTemplate());
    String gerritName2 = "gerrit-replica-2";
    TestGerrit gerrit2 =
        new TestGerrit(client, testProps, GerritMode.REPLICA, gerritName2, operator.getNamespace());
    gerritCluster.addGerrit(gerrit2.createGerritTemplate());
    gerritCluster.deploy();

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(gerritName + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review [replica]"));

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(gerritName2 + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review [replica]"));
  }

  @Test
  void testGerritReplicaAndPrimaryGerritAreCreated() throws Exception {
    String primaryGerritName = "gerrit";
    TestGerrit primaryGerrit =
        new TestGerrit(
            client, testProps, GerritMode.PRIMARY, primaryGerritName, operator.getNamespace());
    gerritCluster.addGerrit(primaryGerrit.createGerritTemplate());
    String gerritReplicaName = "gerrit-replica";
    TestGerrit gerritReplica =
        new TestGerrit(
            client, testProps, GerritMode.REPLICA, gerritReplicaName, operator.getNamespace());
    gerritCluster.addGerrit(gerritReplica.createGerritTemplate());
    gerritCluster.deploy();

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(primaryGerritName + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review"));

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(gerritReplicaName + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review [replica]"));
  }

  @Test
  void testRestartHandlingOnConfigChange() throws Exception {
    String gerritName = "gerrit";
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.PRIMARY, gerritName, operator.getNamespace());
    GerritTemplate gerritTemplate = gerrit.createGerritTemplate();
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();

    gerritCluster.removeGerrit(gerritTemplate);
    GerritServiceConfig svcConfig = new GerritServiceConfig();
    int changedPort = 8081;
    svcConfig.setHttpPort(changedPort);
    GerritTemplateSpec gerritSpec = gerritTemplate.getSpec();
    gerritSpec.setService(svcConfig);
    gerritTemplate.setSpec(gerritSpec);
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();

    await()
        .atMost(30, SECONDS)
        .untilAsserted(
            () -> {
              assertTrue(
                  client
                      .services()
                      .inNamespace(operator.getNamespace())
                      .withName(gerritName)
                      .get()
                      .getSpec()
                      .getPorts()
                      .stream()
                      .anyMatch(port -> port.getPort() == changedPort));
            });
    Mockito.verify(gerritReconciler, times(1)).restartGerritStatefulSet(any());

    gerrit.modifyGerritConfig("test", "test", "test");
    gerritTemplate = gerrit.createGerritTemplate();
    gerritCluster.removeGerrit(gerritTemplate);
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              Mockito.verify(gerritReconciler, times(2)).restartGerritStatefulSet(any());
            });

    secureConfig.modify("test", "test", "test");

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              Mockito.verify(gerritReconciler, times(3)).restartGerritStatefulSet(any());
            });
  }
}
