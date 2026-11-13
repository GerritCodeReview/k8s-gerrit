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

import static com.google.gerrit.k8s.operator.admission.servlet.GerritClusterAdmissionWebhook.GERRIT_MULTISITE_MISCONFIGURED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.test.TestGerrit;
import com.google.gerrit.k8s.operator.test.TestGerritCluster;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritMultisiteClusterAdmissionWebhookTest extends AdmissionWebhookAbstractTest {

  @Test
  public void testMultipleGerritIsNotAcceptedInGerritMultisiteCluster() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);

    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.createClient(), NAMESPACE);
    GerritTemplate primary = TestGerrit.createGerritTemplate("gerrit", GerritMode.PRIMARY, cfg);
    GerritTemplate replica = TestGerrit.createGerritTemplate("gerrit", GerritMode.REPLICA, cfg);
    gerritCluster.addGerrit(primary);
    gerritCluster.addGerrit(replica);

    HttpURLConnection http = sendAdmissionRequest(gerritCluster.build());
    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getCode(), is(equalTo(HttpServletResponse.SC_CONFLICT)));
    assertThat(
        response.getResponse().getStatus().getMessage(),
        is(equalTo(GERRIT_MULTISITE_MISCONFIGURED)));
  }

  @Test
  public void testOneSingleReplicaGerritIsNotAcceptedInGerritMultisiteCluster() throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);

    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.createClient(), NAMESPACE);
    GerritTemplate replica = TestGerrit.createGerritTemplate("gerrit", GerritMode.REPLICA, cfg);
    gerritCluster.addGerrit(replica);

    HttpURLConnection http = sendAdmissionRequest(gerritCluster.build());
    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getCode(), is(equalTo(HttpServletResponse.SC_CONFLICT)));
    assertThat(
        response.getResponse().getStatus().getMessage(),
        is(equalTo(GERRIT_MULTISITE_MISCONFIGURED)));
  }

  @Test
  public void testOneSinglePrimaryGerritAndOnlyOneReplicaIsNotAcceptedInGerritMultisiteCluster()
      throws Exception {
    Config cfg = new Config();
    cfg.fromText(TestGerrit.DEFAULT_GERRIT_CONFIG);

    TestGerritCluster gerritCluster =
        new TestGerritCluster(kubernetesServer.createClient(), NAMESPACE);
    GerritTemplate primary = TestGerrit.createGerritTemplate("gerrit", GerritMode.PRIMARY, cfg);
    primary.getSpec().setReplicas(1);
    gerritCluster.addGerrit(primary);

    HttpURLConnection http = sendAdmissionRequest(gerritCluster.build());
    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getCode(), is(equalTo(HttpServletResponse.SC_CONFLICT)));
    assertThat(
        response.getResponse().getStatus().getMessage(),
        is(equalTo(GERRIT_MULTISITE_MISCONFIGURED)));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.MULTISITE;
  }

  @Override
  protected String getCustomResource() {
    return Constants.GERRIT_CLUSTER_KIND;
  }
}
