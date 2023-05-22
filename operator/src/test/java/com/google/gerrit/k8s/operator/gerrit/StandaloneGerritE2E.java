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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import com.google.gerrit.k8s.operator.cluster.model.GerritClusterIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.gerrit.model.GerritServiceConfig;
import com.google.gerrit.k8s.operator.gerrit.model.GerritSpec;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class StandaloneGerritE2E extends AbstractGerritOperatorE2ETest {

  @Test
  void testPrimaryGerritIsCreated() throws Exception {
    gerritCluster.setIngressType(IngressType.INGRESS);

    String gerritName = "gerrit";
    TestGerrit testGerrit = new TestGerrit(client, testProps, gerritName, operator.getNamespace());
    testGerrit.deploy();

    assertTrue(
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(gerritName + "-0")
            .inContainer("gerrit")
            .getLog()
            .contains("Gerrit Code Review"));
  }

  @Test
  void testGerritReplicaIsCreated() throws Exception {
    String gerritName = "gerrit-replica";
    TestGerrit testGerrit =
        new TestGerrit(client, testProps, GerritMode.REPLICA, gerritName, operator.getNamespace());
    testGerrit.deploy();

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
  void testRestartHandlingOnConfigChange() {
    String gerritName = "gerrit";
    TestGerrit testGerrit = new TestGerrit(client, testProps, gerritName, operator.getNamespace());
    testGerrit.deploy();

    GerritServiceConfig svcConfig = new GerritServiceConfig();
    int changedPort = 8081;
    svcConfig.setHttpPort(changedPort);
    GerritSpec gerritSpec = testGerrit.getSpec();
    gerritSpec.setService(svcConfig);
    testGerrit.setSpec(gerritSpec);

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
    Mockito.verify(gerritReconciler, times(0)).restartGerritStatefulSet(any());

    testGerrit.modifyGerritConfig("test", "test", "test");
    testGerrit.deploy();

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              Mockito.verify(gerritReconciler, times(1)).restartGerritStatefulSet(any());
            });

    secureConfig.modify("test", "test", "test");

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              Mockito.verify(gerritReconciler, times(2)).restartGerritStatefulSet(any());
            });
  }
}
