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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.shared.HttpSshServiceConfig;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GerritConfigReconciliationE2E extends AbstractGerritOperatorE2ETest {
  private static final String GERRIT_NAME = "gerrit";
  private static final String RESTART_ANNOTATION = "kubectl.kubernetes.io/restartedAt";

  private GerritTemplate gerritTemplate;

  @BeforeEach
  public void setupGerrit() throws Exception {
    TestGerrit gerrit =
        new TestGerrit(client, testProps, GerritMode.PRIMARY, GERRIT_NAME, operator.getNamespace());
    gerritTemplate = gerrit.createGerritTemplate();
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();
  }

  @Test
  void testNoRestartIfGerritConfigUnchanged() throws Exception {
    Map<String, String> annotations = getReplicaSetAnnotations();
    assertFalse(annotations.containsKey(RESTART_ANNOTATION));

    gerritCluster.removeGerrit(gerritTemplate);
    GerritTemplateSpec gerritSpec = gerritTemplate.getSpec();
    HttpSshServiceConfig gerritServiceConfig = new HttpSshServiceConfig();
    gerritServiceConfig.setHttpPort(48080);
    gerritSpec.setService(gerritServiceConfig);
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
                      .withName(GERRIT_NAME)
                      .get()
                      .getSpec()
                      .getPorts()
                      .stream()
                      .allMatch(p -> p.getPort() == 48080));
              assertFalse(getReplicaSetAnnotations().containsKey(RESTART_ANNOTATION));
            });
  }

  @Test
  void testRestartOnGerritConfigMapChange() throws Exception {
    String podV1Uid =
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(GERRIT_NAME + "-0")
            .get()
            .getMetadata()
            .getUid();

    gerritCluster.removeGerrit(gerritTemplate);
    GerritTemplateSpec gerritSpec = gerritTemplate.getSpec();
    Map<String, String> cfgs = new HashMap<>();
    cfgs.putAll(gerritSpec.getConfigFiles());
    cfgs.put("test.config", "[test]\n  test");
    gerritSpec.setConfigFiles(cfgs);
    gerritTemplate.setSpec(gerritSpec);
    gerritCluster.addGerrit(gerritTemplate);
    gerritCluster.deploy();

    assertGerritRestart(podV1Uid);
  }

  @Test
  void testRestartOnGerritSecretChange() throws Exception {
    String podV1Uid =
        client
            .pods()
            .inNamespace(operator.getNamespace())
            .withName(GERRIT_NAME + "-0")
            .get()
            .getMetadata()
            .getUid();

    secureConfig.modify("test", "test", "test");

    assertGerritRestart(podV1Uid);
  }

  private void assertGerritRestart(String uidOld) {
    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertTrue(
                  client
                      .pods()
                      .inNamespace(operator.getNamespace())
                      .withName(GERRIT_NAME + "-0")
                      .isReady());
              assertTrue(getReplicaSetAnnotations().containsKey(RESTART_ANNOTATION));
              assertFalse(
                  uidOld.equals(
                      client
                          .pods()
                          .inNamespace(operator.getNamespace())
                          .withName(GERRIT_NAME + "-0")
                          .get()
                          .getMetadata()
                          .getUid()));
            });
  }

  private Map<String, String> getReplicaSetAnnotations() {
    return client
        .apps()
        .statefulSets()
        .inNamespace(operator.getNamespace())
        .withName(GERRIT_NAME)
        .get()
        .getSpec()
        .getTemplate()
        .getMetadata()
        .getAnnotations();
  }

  @Override
  protected IngressType getIngressType() {
    return IngressType.INGRESS;
  }
}
