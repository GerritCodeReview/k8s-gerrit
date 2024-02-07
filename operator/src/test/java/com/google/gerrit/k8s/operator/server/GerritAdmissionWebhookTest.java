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
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.api.model.shared.GerritClusterIngressConfig;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritAdmissionWebhookTest {
  private static final String NAMESPACE = "test";
  private static final String LIST_GERRITS_PATH =
      String.format(
          "/apis/%s/namespaces/%s/%s",
          HasMetadata.getApiVersion(Gerrit.class), NAMESPACE, HasMetadata.getPlural(Gerrit.class));
  private static final String LIST_GERRIT_CLUSTERS_PATH =
      String.format(
          "/apis/%s/namespaces/%s/%s",
          HasMetadata.getApiVersion(GerritCluster.class),
          NAMESPACE,
          HasMetadata.getPlural(GerritCluster.class));
  private TestAdmissionWebhookServer server;

  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    KubernetesDeserializer.registerCustomKind(
        "gerritoperator.google.com/v1beta1", "Gerrit", Gerrit.class);
    KubernetesDeserializer.registerCustomKind(
        "gerritoperator.google.com/v1beta1", "Receiver", Receiver.class);
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    GerritAdmissionWebhook webhook = new GerritAdmissionWebhook();
    server.registerWebhook(webhook);
    server.start();
  }

  @Test
  public void testInvalidGerritConfigRejected() throws Exception {
    String clusterName = "gerrit";
    Config gerritConfig = new Config();
    gerritConfig.setString("container", null, "user", "gerrit");
    Gerrit gerrit = createGerrit(clusterName, gerritConfig);
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRITS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, new DefaultKubernetesResourceList<Gerrit>())
        .times(2);

    mockGerritCluster(clusterName);

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    gerritConfig.setString("container", null, "user", "invalid");
    Gerrit gerrit2 = createGerrit(clusterName, gerritConfig);
    HttpURLConnection http2 = sendAdmissionRequest(gerrit2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
  }

  private void mockGerritCluster(String name) {
    GerritCluster cluster = new GerritCluster();
    cluster.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(NAMESPACE).build());
    GerritClusterSpec clusterSpec = new GerritClusterSpec();
    GerritClusterIngressConfig ingressConfig = new GerritClusterIngressConfig();
    ingressConfig.setEnabled(false);
    clusterSpec.setIngress(ingressConfig);
    clusterSpec.setServerId("test");
    cluster.setSpec(clusterSpec);

    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRIT_CLUSTERS_PATH + "/" + name)
        .andReturn(HttpURLConnection.HTTP_OK, cluster)
        .always();
  }

  private Gerrit createGerrit(String cluster, Config gerritConfig) {
    ObjectMeta meta =
        new ObjectMetaBuilder()
            .withName(RandomStringUtils.random(10))
            .withNamespace(NAMESPACE)
            .build();
    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setMode(GerritMode.PRIMARY);
    if (gerritConfig != null) {
      gerritSpec.setConfigFiles(Map.of("gerrit.config", gerritConfig.toText()));
    }
    Gerrit gerrit = new Gerrit();
    gerrit.setMetadata(meta);
    gerrit.setSpec(gerritSpec);
    return gerrit;
  }

  private HttpURLConnection sendAdmissionRequest(Gerrit gerrit)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL("http://localhost:" + PORT + "/admission/v1beta1/gerrit").openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(gerrit);
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
