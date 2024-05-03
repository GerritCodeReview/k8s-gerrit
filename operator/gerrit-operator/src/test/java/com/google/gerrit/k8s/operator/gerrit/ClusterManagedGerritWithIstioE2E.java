// Copyright (C) 2023 The Android Open Source Project
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import org.junit.jupiter.api.Test;

public class ClusterManagedGerritWithIstioE2E extends AbstractGerritOperatorE2ETest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Test
  void testPrimaryGerritWithIstio() throws Exception {
    GerritTemplate gerrit =
        new TestGerrit(client, testProps, GerritMode.PRIMARY, "gerrit", operator.getNamespace())
            .createGerritTemplate();
    gerritCluster.addGerrit(gerrit);
    gerritCluster.deploy();

    GerritApi gerritApi = gerritCluster.getGerritApiClient(gerrit, IngressType.ISTIO);
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

  @Override
  protected IngressType getIngressType() {
    return IngressType.ISTIO;
  }
}
