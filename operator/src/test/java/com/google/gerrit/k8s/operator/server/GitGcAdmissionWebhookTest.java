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
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.admission.servlet.GitGcAdmissionWebhook;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollectionSpec;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
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
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GitGcAdmissionWebhookTest {
  private static final String NAMESPACE = "test";
  private static final String LIST_GITGCS_PATH =
      String.format(
          "/apis/%s/namespaces/%s/%s",
          HasMetadata.getApiVersion(GitGarbageCollection.class),
          NAMESPACE,
          HasMetadata.getPlural(GitGarbageCollection.class));
  private TestAdmissionWebhookServer server;

  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    GitGcAdmissionWebhook webhook = new GitGcAdmissionWebhook(kubernetesServer.getClient());
    server.registerWebhook(webhook);
    server.start();
  }

  @Test
  @DisplayName("Only a single GitGC that works on all projects in site is allowed.")
  public void testOnlySingleGitGcWorkingOnAllProjectsIsAllowed() throws Exception {
    GitGarbageCollection gitGc = createCompleteGitGc();
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK, new DefaultKubernetesResourceList<GitGarbageCollection>())
        .once();

    HttpURLConnection http = sendAdmissionRequest(gitGc);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    DefaultKubernetesResourceList<GitGarbageCollection> existingGitGcs =
        new DefaultKubernetesResourceList<GitGarbageCollection>();
    existingGitGcs.setItems(List.of(createCompleteGitGc()));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGitGcs)
        .once();

    HttpURLConnection http2 = sendAdmissionRequest(gitGc);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  @Test
  @DisplayName(
      "A GitGc configured to work on all projects and selective GitGcs are allowed to exist at the same time.")
  public void testSelectiveAndCompleteGitGcAreAllowedTogether() throws Exception {
    DefaultKubernetesResourceList<GitGarbageCollection> existingGitGcs =
        new DefaultKubernetesResourceList<GitGarbageCollection>();
    existingGitGcs.setItems(List.of(createCompleteGitGc()));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGitGcs)
        .once();

    GitGarbageCollection gitGc2 = createGitGcForProjects(Set.of("project3"));
    HttpURLConnection http2 = sendAdmissionRequest(gitGc2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  @Test
  @DisplayName("Multiple selectve GitGcs working on a different set of projects are allowed.")
  public void testNonConflictingSelectiveGcsAreAllowed() throws Exception {
    GitGarbageCollection gitGc = createGitGcForProjects(Set.of("project1", "project2"));
    DefaultKubernetesResourceList<GitGarbageCollection> existingGitGcs =
        new DefaultKubernetesResourceList<GitGarbageCollection>();
    existingGitGcs.setItems(List.of(gitGc));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGitGcs)
        .once();

    GitGarbageCollection gitGc2 = createGitGcForProjects(Set.of("project3"));
    HttpURLConnection http2 = sendAdmissionRequest(gitGc2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  @Test
  @DisplayName("Multiple selectve GitGcs working on the same project(s) are not allowed.")
  public void testConflictingSelectiveGcsNotAllowed() throws Exception {
    GitGarbageCollection gitGc = createGitGcForProjects(Set.of("project1", "project2"));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK, new DefaultKubernetesResourceList<GitGarbageCollection>())
        .once();

    HttpURLConnection http = sendAdmissionRequest(gitGc);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    DefaultKubernetesResourceList<GitGarbageCollection> existingGitGcs =
        new DefaultKubernetesResourceList<GitGarbageCollection>();
    existingGitGcs.setItems(List.of(gitGc));
    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_GITGCS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, existingGitGcs)
        .once();

    GitGarbageCollection gitGc2 = createGitGcForProjects(Set.of("project1"));
    HttpURLConnection http2 = sendAdmissionRequest(gitGc2);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  private GitGarbageCollection createCompleteGitGc() {
    return createGitGcForProjects(Set.of());
  }

  private GitGarbageCollection createGitGcForProjects(Set<String> projects) {
    GitGarbageCollectionSpec spec = new GitGarbageCollectionSpec();
    spec.setProjects(projects);
    GitGarbageCollection gitGc = new GitGarbageCollection();
    gitGc.setMetadata(
        new ObjectMetaBuilder()
            .withName(RandomStringUtils.randomAlphabetic(10))
            .withUid(RandomStringUtils.randomAlphabetic(10))
            .withNamespace(NAMESPACE)
            .build());
    gitGc.setSpec(spec);
    return gitGc;
  }

  private HttpURLConnection sendAdmissionRequest(GitGarbageCollection gitGc)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL(
                    "http://localhost:"
                        + PORT
                        + "/admission/"
                        + Constants.VERSION
                        + "/gitgarbagecollections")
                .openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(gitGc);
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
