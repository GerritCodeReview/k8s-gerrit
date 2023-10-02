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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.v1alpha.api.model.gerrit.GerritTemplateSpec.GerritMode;
import org.junit.jupiter.api.Test;

public class StandaloneGerritE2E extends AbstractGerritOperatorE2ETest {

  @Test
  void testPrimaryGerritIsCreated() throws Exception {
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

  @Override
  protected IngressType getIngressType() {
    return IngressType.INGRESS;
  }
}
