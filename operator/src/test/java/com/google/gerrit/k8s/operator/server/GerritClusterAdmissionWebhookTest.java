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

import static com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer.PORT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritClusterAdmissionWebhook;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplate;
import com.google.gerrit.k8s.operator.api.model.receiver.ReceiverTemplateSpec;
import com.google.gerrit.k8s.operator.test.ReceiverUtil;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.test.TestGerritCluster;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritClusterAdmissionWebhookTest {
  private static final String NAMESPACE = "test";
  private TestAdmissionWebhookServer server;

  private ClusterMode highAvailability = ClusterMode.HIGH_AVAILABILITY;

  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    server.registerWebhook(new GerritClusterAdmissionWebhook(highAvailability));
    server.registerWebhook(new GerritAdmissionWebhook(highAvailability));
    server.start();
  }

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

  private HttpURLConnection sendAdmissionRequest(GerritCluster gerritCluster)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL("http://localhost:" + PORT + "/admission/v1beta1/gerritcluster")
                .openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(gerritCluster);
    AdmissionReview admissionReview = new AdmissionReview();
    admissionReview.setRequest(admissionReq);

    try (OutputStream os = http.getOutputStream()) {
      byte[] input = new ObjectMapper().writer().writeValueAsBytes(admissionReview);
      os.write(input, 0, input.length);
    }
    return http;
  }

  @AfterAll
  public void shutdown() throws Exception {
    server.stop();
  }
}
