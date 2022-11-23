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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.cluster.GerritIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec.GerritMode;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.test.TestGerritCluster;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(Lifecycle.PER_CLASS)
public class ReceiverE2E extends AbstractGerritOperatorE2ETest {
  private static final String CREDENTIALS_SECRET_NAME = "receiver-secret";
  private static final String USER = "git";
  private static final String PASSWORD = RandomStringUtils.randomAlphanumeric(32);

  private Receiver receiver;
  private TestGerrit gerritReplica;

  @BeforeAll
  public void setupReceiver() {
    receiver = new Receiver();
    ObjectMeta receiverMeta = new ObjectMetaBuilder().withName("receiver").build();
    receiver.setMetadata(receiverMeta);
    ReceiverSpec receiverSpec = new ReceiverSpec();
    receiverSpec.setCluster(TestGerritCluster.CLUSTER_NAME);
    receiverSpec.setReplicas(2);
    receiverSpec.setCredentialSecretRef(CREDENTIALS_SECRET_NAME);
    receiver.setSpec(receiverSpec);
  }

  @BeforeEach
  public void setupComponents() {
    gerritCluster.setIngressType(IngressType.INGRESS);
    createCredentialsSecret();
    client.resource(receiver).inNamespace(operator.getNamespace()).createOrReplace();
    awaitReceiverReadiness();

    gerritReplica = new TestGerrit(client, testProps, gerritCluster, GerritMode.REPLICA);
    gerritReplica.deploy();
  }

  @Test
  public void testProjectLifecycle(@TempDir Path tempDir) throws Exception {
    assertThat(sendReceiverApiRequest("GET", "/new/testLegacy.git"), is(equalTo(201)));
    assertThat(sendReceiverApiRequest("PUT", "/a/projects/test.git"), is(equalTo(201)));
    CredentialsProvider gerritCredentials =
        new UsernamePasswordCredentialsProvider(
            testProps.getGerritUser(), testProps.getGerritPwd());
    Git git =
        Git.cloneRepository()
            .setURI(getGerritUrl("/a/test.git").toString())
            .setCredentialsProvider(gerritCredentials)
            .setDirectory(tempDir.toFile())
            .call();
    new File("test.txt").createNewFile();
    git.add().addFilepattern(".").call();
    RevCommit commit = git.commit().setMessage("test commit").call();
    git.remoteAdd()
        .setName("receiver")
        .setUri(new URIish(getReceiverUrl("/git/test.git").toString()))
        .call();
    git.push()
        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(USER, PASSWORD))
        .setRemote("receiver")
        .setRefSpecs(new RefSpec("refs/heads/master"))
        .call();
    assertTrue(
        git.lsRemote().setCredentialsProvider(gerritCredentials).setRemote("origin").call().stream()
            .anyMatch(ref -> ref.getObjectId().equals(commit.getId())));
    assertThat(sendReceiverApiRequest("DELETE", "/a/projects/test.git"), is(equalTo(204)));
  }

  private void awaitReceiverReadiness() {
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

  private int sendReceiverApiRequest(String method, String path) throws Exception {
    URL url = getReceiverUrl(path);

    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    try {
      con.setRequestMethod(method);
      String encodedAuth =
          Base64.getEncoder()
              .encodeToString(
                  String.format("%s:%s", USER, PASSWORD).getBytes(StandardCharsets.UTF_8));
      con.setRequestProperty("Authorization", "Basic " + encodedAuth);
      return con.getResponseCode();
    } finally {
      con.disconnect();
    }
  }

  private URL getReceiverUrl(String path) throws Exception {
    return new URIBuilder()
        .setScheme("https")
        .setHost(
            String.format("%s.%s", receiver.getMetadata().getName(), testProps.getIngressDomain()))
        .setPath(path)
        .build()
        .toURL();
  }

  private URL getGerritUrl(String path) throws Exception {
    return new URIBuilder()
        .setScheme("https")
        .setHost(String.format("%s.%s", TestGerrit.NAME, testProps.getIngressDomain()))
        .setPath(path)
        .build()
        .toURL();
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
