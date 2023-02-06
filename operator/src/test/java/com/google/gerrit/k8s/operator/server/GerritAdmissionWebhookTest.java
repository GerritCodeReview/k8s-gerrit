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
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec.GerritMode;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.receiver.Receiver;
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
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.http.HttpMethod;
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
  private TestAdmissionWebhookServer server;

  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    KubernetesDeserializer.registerCustomKind(
        "gerritoperator.google.com/v1alpha1", "Gerrit", Gerrit.class);
    KubernetesDeserializer.registerCustomKind(
        "gerritoperator.google.com/v1alpha1", "Receiver", Receiver.class);
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    GerritAdmissionWebhook webhook = new GerritAdmissionWebhook(kubernetesServer.getClient());
    server.registerWebhook(webhook);
    server.start();
  }

  @Test
  public void testGerritClusterNameIsRequired() throws Exception {
    Gerrit gerrit = createGerrit("");
    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_BAD_REQUEST)));
  }

  @Test
  public void testOnlySinglePrimaryGerritIsAcceptedPerGerritCluster() throws Exception {
    Gerrit gerrit = createGerrit("gerrit");
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRITS_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK, new DefaultKubernetesResourceList<GitGarbageCollection>())
        .once();

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    DefaultKubernetesResourceList<Gerrit> existingGerrits =
        new DefaultKubernetesResourceList<Gerrit>();
    existingGerrits.setItems(List.of(gerrit));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRITS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGerrits)
        .once();

    Gerrit gerrit2 = createGerrit("gerrit");
    HttpURLConnection http2 = sendAdmissionRequest(gerrit2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  @Test
  public void testMultiplePrimaryGerritsAllowedIfInDifferentGerritCluster() throws Exception {
    Gerrit gerrit = createGerrit("gerrit");
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRITS_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK, new DefaultKubernetesResourceList<GitGarbageCollection>())
        .once();

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    DefaultKubernetesResourceList<Gerrit> existingGerrits =
        new DefaultKubernetesResourceList<Gerrit>();
    existingGerrits.setItems(List.of(gerrit));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GERRITS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGerrits)
        .once();

    Gerrit gerrit2 = createGerrit("other_gerrit");
    HttpURLConnection http2 = sendAdmissionRequest(gerrit2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  private Gerrit createGerrit(String cluster) {
    ObjectMeta meta =
        new ObjectMetaBuilder()
            .withName(RandomStringUtils.random(10))
            .withNamespace(NAMESPACE)
            .build();
    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setCluster(cluster);
    gerritSpec.setMode(GerritMode.PRIMARY);
    Gerrit gerrit = new Gerrit();
    gerrit.setMetadata(meta);
    gerrit.setSpec(gerritSpec);
    return gerrit;
  }

  private HttpURLConnection sendAdmissionRequest(Gerrit gerrit)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection) new URL("http://localhost:8080/admission/gerrit").openConnection();
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
