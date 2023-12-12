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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gerrit.k8s.operator.test.AbstractGerritOperatorE2ETest;
import com.google.gerrit.k8s.operator.test.ReceiverUtil;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.v1beta3.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.v1beta3.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.v1beta3.api.model.receiver.ReceiverTemplateSpec;
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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractClusterManagedReceiverE2E extends AbstractGerritOperatorE2ETest {
  private static final String GERRIT_NAME = "gerrit";
  private ReceiverTemplate receiver;
  private GerritTemplate gerrit;

  @BeforeEach
  public void setupComponents() throws Exception {
    gerrit = TestGerrit.createGerritTemplate(GERRIT_NAME, GerritMode.REPLICA);
    gerritCluster.addGerrit(gerrit);

    receiver = new ReceiverTemplate();
    ObjectMeta receiverMeta = new ObjectMetaBuilder().withName("receiver").build();
    receiver.setMetadata(receiverMeta);
    ReceiverTemplateSpec receiverTemplateSpec = new ReceiverTemplateSpec();
    receiverTemplateSpec.setReplicas(2);
    receiverTemplateSpec.setCredentialSecretRef(ReceiverUtil.CREDENTIALS_SECRET_NAME);
    receiver.setSpec(receiverTemplateSpec);
    gerritCluster.setReceiver(receiver);
    gerritCluster.deploy();
  }

  @Test
  public void testProjectLifecycle(@TempDir Path tempDir) throws Exception {
    GerritCluster cluster = gerritCluster.getGerritCluster();
    assertProjectLifecycle(cluster, tempDir);
  }

  private void assertProjectLifecycle(GerritCluster cluster, Path tempDir) throws Exception {
    assertThat(
        ReceiverUtil.sendReceiverApiRequest(cluster, "PUT", "/a/projects/test.git"),
        is(equalTo(201)));
    CredentialsProvider gerritCredentials =
        new UsernamePasswordCredentialsProvider(
            testProps.getGerritUser(), testProps.getGerritPwd());
    Git git =
        Git.cloneRepository()
            .setURI(getGerritUrl("/test.git").toString())
            .setCredentialsProvider(gerritCredentials)
            .setDirectory(tempDir.toFile())
            .call();
    new File("test.txt").createNewFile();
    git.add().addFilepattern(".").call();
    RevCommit commit = git.commit().setMessage("test commit").call();
    git.push()
        .setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(
                ReceiverUtil.RECEIVER_TEST_USER, ReceiverUtil.RECEIVER_TEST_PASSWORD))
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
        .setHost(gerritCluster.getHostname())
        .setPath(path)
        .build()
        .toURL();
  }
}
