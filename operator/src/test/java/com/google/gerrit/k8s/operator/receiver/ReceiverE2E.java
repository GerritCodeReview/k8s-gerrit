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

package com.google.gerrit.k8s.operator.receiver;

import static com.google.gerrit.k8s.operator.cluster.GerritIngress.INGRESS_NAME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerritCluster;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class ReceiverE2E extends AbstractGerritOperatorE2ETest {
  private static final String CREDENTIALS_SECRET_NAME = "receiver-secret";
  private static final String USER = "git";
  private static final String PASSWORD = RandomStringUtils.randomAlphanumeric(32);

  @Test
  public void testReceiverDeployment() {
    gerritCluster.setIngressEnabled(true);
    createCredentialsSecret();

    Receiver receiver = new Receiver();
    ObjectMeta receiverMeta =
        new ObjectMetaBuilder().withName("receiver").withNamespace(operator.getNamespace()).build();
    receiver.setMetadata(receiverMeta);
    ReceiverSpec receiverSpec = new ReceiverSpec();
    receiverSpec.setCluster(TestGerritCluster.CLUSTER_NAME);
    receiverSpec.setReplicas(2);
    receiverSpec.setCredentialSecretRef(CREDENTIALS_SECRET_NAME);
    receiver.setSpec(receiverSpec);
    client.resource(receiver).createOrReplace();

    await()
        .atMost(1, MINUTES)
        .untilAsserted(
            () -> {
              assertThat(
                  client
                      .services()
                      .inNamespace(operator.getNamespace())
                      .withName(ReceiverServiceDependentResource.getName(receiver))
                      .get(),
                  is(notNullValue()));
            });

    await()
        .atMost(2, MINUTES)
        .untilAsserted(
            () -> {
              assertTrue(
                  client
                      .apps()
                      .deployments()
                      .inNamespace(operator.getNamespace())
                      .withName(receiver.getMetadata().getName())
                      .isReady());
            });

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
  }

  private void createCredentialsSecret() {
    String enPasswd = Md5Crypt.md5Crypt(PASSWORD.getBytes());
    String htpasswdContent = USER + ":" + enPasswd;
    Secret sec =
        new SecretBuilder()
            .withNewMetadata()
            .withNamespace(operator.getNamespace())
            .withName(CREDENTIALS_SECRET_NAME)
            .endMetadata()
            .withData(
                Map.of(".htpasswd", Base64.getEncoder().encodeToString(htpasswdContent.getBytes())))
            .build();
    client.resource(sec).createOrReplace();
  }
}
