// Copyright (C) 2024 The Android Open Source Project
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
import com.google.gerrit.k8s.operator.admission.servlet.GerritMaintenanceAdmissionWebhook;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.maintenance.GerritMaintenanceTestHelper;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritMaintenanceAdmissionWebhookTest {
  private static final String NAMESPACE = "test";
  private TestAdmissionWebhookServer server;

  @BeforeAll
  public void setup() throws Exception {
    server = new TestAdmissionWebhookServer();
    GerritMaintenanceAdmissionWebhook webhook = new GerritMaintenanceAdmissionWebhook();
    server.registerWebhook(webhook);
    server.start();
  }

  @Test
  @DisplayName("Only a single GitGC that works on all projects in site is allowed.")
  public void testOnlySingleGitGcWorkingOnAllProjectsIsAllowed() throws Exception {
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(NAMESPACE, List.of(Set.of()));
    HttpURLConnection http = sendAdmissionRequest(gm);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));

    GerritMaintenance gmConflict =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            NAMESPACE, List.of(Set.of(), Set.of()));

    HttpURLConnection http2 = sendAdmissionRequest(gmConflict);

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
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            NAMESPACE, List.of(Set.of(), Set.of("project1")));

    HttpURLConnection http2 = sendAdmissionRequest(gm);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  @Test
  @DisplayName("Multiple selectve GitGcs working on a different set of projects are allowed.")
  public void testNonConflictingSelectiveGcsAreAllowed() throws Exception {
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            NAMESPACE, List.of(Set.of("project1"), Set.of("project2")));

    HttpURLConnection http2 = sendAdmissionRequest(gm);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(true));
  }

  @Test
  @DisplayName("Multiple selectve GitGcs working on the same project(s) are not allowed.")
  public void testConflictingSelectiveGcsNotAllowed() throws Exception {
    GerritMaintenance gm =
        GerritMaintenanceTestHelper.createGerritMaintenanceWithGitGcs(
            NAMESPACE, List.of(Set.of("project1"), Set.of("project1")));
    HttpURLConnection http2 = sendAdmissionRequest(gm);

    AdmissionReview response2 =
        new ObjectMapper().readValue(http2.getInputStream(), AdmissionReview.class);

    assertThat(http2.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response2.getResponse().getAllowed(), is(false));
    assertThat(
        response2.getResponse().getStatus().getCode(),
        is(equalTo(HttpServletResponse.SC_CONFLICT)));
  }

  private HttpURLConnection sendAdmissionRequest(GerritMaintenance gm)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL(
                    "http://localhost:"
                        + PORT
                        + "/admission/"
                        + Constants.VERSION
                        + "/"
                        + Constants.GERRIT_MAINTENANCE_KIND)
                .openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(gm);
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
