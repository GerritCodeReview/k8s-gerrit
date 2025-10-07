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

package com.google.gerrit.k8s.operator.server;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplateSpec;
import com.google.gerrit.k8s.operator.test.ReceiverUtil;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.test.TestGerritCluster;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritClusterAdmissionWebhookTest extends AdmissionWebhookAbstractTest {

  @Test
  public void testOnlySinglePrimaryGerritIsAcceptedPerGerritCluster() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);
    GerritTemplate gerrit1 = TestGerrit.createGerritTemplate("gerrit1", GerritMode.PRIMARY, cfg);
    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.getClient(), NAMESPACE);
    gerritCluster.addGerrit(gerrit1);
    GerritCluster cluster = gerritCluster.build();

    HttpURLConnection http = sendAdmissionRequest(cluster);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    GerritTemplate gerrit2 = TestGerrit.createGerritTemplate("gerrit2", GerritMode.PRIMARY, cfg);
    gerritCluster.addGerrit(gerrit2);
    HttpURLConnection http2 = sendAdmissionRequest(gerritCluster.build());

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  @Test
  public void testPrimaryGerritAndReceiverAreNotAcceptedInSameGerritCluster() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);
    GerritTemplate gerrit = TestGerrit.createGerritTemplate("gerrit1", GerritMode.PRIMARY, cfg);
    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.getClient(), NAMESPACE);
    gerritCluster.addGerrit(gerrit);

    ReceiverTemplate receiver = new ReceiverTemplate();
    ObjectMeta receiverMeta = new ObjectMetaBuilder().withName("receiver").build();
    receiver.setMetadata(receiverMeta);
    ReceiverTemplateSpec receiverTemplateSpec = new ReceiverTemplateSpec();
    receiverTemplateSpec.setReplicas(2);
    receiverTemplateSpec.setCredentialSecretRef(ReceiverUtil.CREDENTIALS_SECRET_NAME);
    receiver.setSpec(receiverTemplateSpec);

    gerritCluster.setReceiver(receiver);
    HttpURLConnection http2 = sendAdmissionRequest(gerritCluster.build());

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  @Test
  public void testPrimaryAndReplicaAreAcceptedInSameGerritCluster() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);
    GerritTemplate gerrit1 = TestGerrit.createGerritTemplate("gerrit1", GerritMode.PRIMARY, cfg);
    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.getClient(), NAMESPACE);
    gerritCluster.addGerrit(gerrit1);

    HttpURLConnection http = sendAdmissionRequest(gerritCluster.build());

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    GerritTemplate gerrit2 = TestGerrit.createGerritTemplate("gerrit2", GerritMode.REPLICA, cfg);
    gerritCluster.addGerrit(gerrit2);
    HttpURLConnection http2 = sendAdmissionRequest(gerritCluster.build());

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  @Test
  public void testPrimaryGerritAndReplicaMustHaveUniqueName() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);

    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.getClient(), NAMESPACE);
    GerritTemplate primary = TestGerrit.createGerritTemplate("gerrit", GerritMode.PRIMARY, cfg);
    GerritTemplate replica = TestGerrit.createGerritTemplate("gerrit", GerritMode.REPLICA, cfg);
    gerritCluster.addGerrit(primary);
    gerritCluster.addGerrit(replica);

    HttpURLConnection http2 = sendAdmissionRequest(gerritCluster.build());
    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  @Override
  protected String getCustomResource() {
    return Constants.GERRIT_CLUSTER_KIND;
  }
}
