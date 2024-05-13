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

import static com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook.NO_EVENTS_BROKER_CONFIGURED_MSG;
import static com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook.NO_REFDB_CONFIGURED_MSG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.shared.*;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritMultisiteAdmissionWebhookTest extends AdmissionWebhookAbstractTest {

  private static final String CLUSTER_NAME = "gerrit";

  @Test
  public void testNoRefDbConfiguredForMultisiteRejected() throws Exception {
    Gerrit gerrit = createGerritWithConfig(false, false);
    mockGerritCluster(CLUSTER_NAME);

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getMessage(), is(equalTo(NO_REFDB_CONFIGURED_MSG)));
  }

  @Test
  public void testNoEventsBrokerConfiguredForMultisiteRejected() throws Exception {
    Gerrit gerrit = createGerritWithConfig(true, false);
    mockGerritCluster(CLUSTER_NAME);

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(false));
    assertThat(
        response.getResponse().getStatus().getMessage(),
        is(equalTo(NO_EVENTS_BROKER_CONFIGURED_MSG)));
  }

  @Test
  public void testMultisiteConfiguredNotRejected() throws Exception {
    Gerrit gerrit = createGerritWithConfig(true, true);
    mockGerritCluster(CLUSTER_NAME);

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));
  }

  private Gerrit createGerritWithConfig(boolean withRefdb, boolean withEventsBroker) {
    Config gerritConfig = new Config();
    Gerrit gerrit = createGerrit(CLUSTER_NAME, gerritConfig);
    if (withRefdb) {
      GlobalRefDbConfig refDb = new GlobalRefDbConfig();
      refDb.setDatabase(GlobalRefDbConfig.RefDatabase.ZOOKEEPER);
      ZookeeperRefDbConfig zk = new ZookeeperRefDbConfig();
      refDb.setZookeeper(zk);
      gerrit.getSpec().setRefdb(refDb);
    }
    if (withEventsBroker) {
      EventsBrokerConfig broker = new EventsBrokerConfig();
      broker.setBrokerType(EventsBrokerConfig.BrokerType.KAFKA);
      KafkaConfig kafkaConfig = new KafkaConfig();
      kafkaConfig.setConnectString("kafka:9092");
      broker.setKafkaConfig(kafkaConfig);
      gerrit.getSpec().setEventsBroker(broker);
    }
    return gerrit;
  }

  @Override
  protected String getCustomResource() {
    return Constants.GERRIT_KIND;
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.MULTISITE;
  }
}
