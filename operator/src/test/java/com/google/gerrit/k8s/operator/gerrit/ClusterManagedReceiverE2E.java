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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.model.GerritClusterIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.receiver.model.ReceiverTemplate;
import com.google.gerrit.k8s.operator.receiver.model.ReceiverTemplateSpec;
import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.ReceiverUtil;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
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
public class ClusterManagedReceiverE2E extends AbstractGerritOperatorE2ETest {
  private static final String GERRIT_NAME = "gerrit";
  private ReceiverTemplate receiver;

  @BeforeAll
  public void setupReceiver() {
    receiver = new ReceiverTemplate();
    ObjectMeta receiverMeta = new ObjectMetaBuilder().withName("receiver").build();
    receiver.setMetadata(receiverMeta);
    ReceiverTemplateSpec receiverTemplateSpec = new ReceiverTemplateSpec();
    receiverTemplateSpec.setReplicas(2);
    receiverTemplateSpec.setCredentialSecretRef(ReceiverUtil.CREDENTIALS_SECRET_NAME);
    receiver.setSpec(receiverTemplateSpec);
  }

  @BeforeEach
  public void setupComponents() throws Exception {
    gerritCluster.setIngressType(IngressType.INGRESS);

    gerritCluster.addGerrit(TestGerrit.createGerritTemplate(GERRIT_NAME, GerritMode.REPLICA));
    gerritCluster.setReceiver(receiver);
    gerritCluster.deploy();
  }

  @Test
  public void testProjectLifecycle(@TempDir Path tempDir) throws Exception {
    GerritCluster cluster = gerritCluster.getGerritCluster();
    assertThat(
        ReceiverUtil.sendReceiverApiRequest(cluster, "GET", "/new/testLegacy.git"),
        is(equalTo(201)));
    assertThat(
        ReceiverUtil.sendReceiverApiRequest(cluster, "PUT", "/a/projects/test.git"),
        is(equalTo(201)));
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
        .setUri(new URIish(ReceiverUtil.getReceiverUrl(cluster, "/git/test.git").toString()))
        .call();
    git.push()
        .setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(
                ReceiverUtil.RECEIVER_TEST_USER, ReceiverUtil.RECEIVER_TEST_PASSWORD))
        .setRemote("receiver")
        .setRefSpecs(new RefSpec("refs/heads/master"))
        .call();
    assertTrue(
        git.lsRemote().setCredentialsProvider(gerritCredentials).setRemote("origin").call().stream()
            .anyMatch(ref -> ref.getObjectId().equals(commit.getId())));
    assertThat(
        ReceiverUtil.sendReceiverApiRequest(cluster, "DELETE", "/a/projects/test.git"),
        is(equalTo(204)));
  }

  private URL getGerritUrl(String path) throws Exception {
    return new URIBuilder()
        .setScheme("https")
        .setHost(String.format("%s.%s", GERRIT_NAME, testProps.getIngressDomain()))
        .setPath(path)
        .build()
        .toURL();
  }
}
