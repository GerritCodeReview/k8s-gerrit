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

package com.google.gerrit.k8s.operator.cluster;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.dependent.NfsIdmapdConfigMap;
import com.google.gerrit.k8s.operator.cluster.dependent.SharedPVC;
import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2EBase;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.junit.jupiter.api.Test;

public class GerritClusterE2E extends AbstractGerritOperatorE2EBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Test
  void testSharedPvcCreated() {
    logger.atInfo().log("Waiting max 1 minutes for the shared pvc to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              PersistentVolumeClaim pvc =
                  client
                      .persistentVolumeClaims()
                      .inNamespace(operator.getNamespace())
                      .withName(SharedPVC.SHARED_PVC_NAME)
                      .get();
              assertThat(pvc, is(notNullValue()));
            });
  }

  @Test
  void testNfsIdmapdConfigMapCreated() {
    gerritCluster.setNfsEnabled(true);
    logger.atInfo().log("Waiting max 1 minutes for the nfs idmapd configmap to be created.");
    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              ConfigMap cm =
                  client
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .withName(NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME)
                      .get();
              assertThat(cm, is(notNullValue()));
            });
  }

  @Override
  protected IngressType getIngressType() {
    return IngressType.INGRESS;
  }
}
