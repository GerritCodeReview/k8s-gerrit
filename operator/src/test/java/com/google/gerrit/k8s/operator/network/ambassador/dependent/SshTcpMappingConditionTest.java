// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import org.junit.jupiter.api.Test;

public class SshTcpMappingConditionTest {

  private final SshTcpMappingCondition condition = new SshTcpMappingCondition();

  @Test
  public void sshEnabledWithPrimaryGerrit() {
    GerritNetwork network =
        ReconcilerUtils.loadYaml(
            GerritNetwork.class,
            this.getClass(),
            "../../gerritnetwork_primary_ambassador_ssh.yaml");
    assertTrue(condition.isMet(null, network, null));
  }

  @Test
  public void sshDisabledByDefault() {
    GerritNetwork network =
        ReconcilerUtils.loadYaml(
            GerritNetwork.class, this.getClass(), "../../gerritnetwork_primary.yaml");
    assertFalse(condition.isMet(null, network, null));
  }

  @Test
  public void sshEnabledWithoutPrimaryGerrit() {
    GerritNetwork network =
        ReconcilerUtils.loadYaml(
            GerritNetwork.class, this.getClass(), "../../gerritnetwork_replica_ssh.yaml");
    assertFalse(condition.isMet(null, network, null));
  }
}
