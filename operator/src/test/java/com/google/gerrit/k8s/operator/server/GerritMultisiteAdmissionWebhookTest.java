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

  private static final String NO_REFDB_CONFIGURED_MSG =
      "A Ref-Database is required to horizontally scale a primary Gerrit: .spec.refdb.database != NONE";
  private static final String NO_EVENTS_BROKER_CONFIGURED_MSG =
      "An events-broker is required to run Gerrit in multisite mode: .spec.eventsBroker.brokerType != NONE";

  @Test
  public void testNoRefDbConfiguredForMultisiteRejected() throws Exception {
    String clusterName = "gerrit";
    Config gerritConfig = new Config();
    Gerrit gerrit = createGerrit(clusterName, gerritConfig);

    mockGerritCluster(clusterName);

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
    String clusterName = "gerrit";
    Config gerritConfig = new Config();
    Gerrit gerrit = createGerrit(clusterName, gerritConfig);
    GlobalRefDbConfig refDb = new GlobalRefDbConfig();
    refDb.setDatabase(GlobalRefDbConfig.RefDatabase.ZOOKEEPER);
    ZookeeperRefDbConfig zk = new ZookeeperRefDbConfig();
    refDb.setZookeeper(zk);
    gerrit.getSpec().setRefdb(refDb);

    mockGerritCluster(clusterName);

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
    String clusterName = "gerrit";
    Config gerritConfig = new Config();
    Gerrit gerrit = createGerrit(clusterName, gerritConfig);
    GlobalRefDbConfig refDb = new GlobalRefDbConfig();
    refDb.setDatabase(GlobalRefDbConfig.RefDatabase.ZOOKEEPER);
    ZookeeperRefDbConfig zk = new ZookeeperRefDbConfig();
    refDb.setZookeeper(zk);
    gerrit.getSpec().setRefdb(refDb);

    EventsBrokerConfig broker = new EventsBrokerConfig();
    broker.setBrokerType(EventsBrokerConfig.BrokerType.KAFKA);
    KafkaConfig kafkaConfig = new KafkaConfig();
    kafkaConfig.setConnectString("kafka:9092");
    broker.setKafkaConfig(kafkaConfig);
    gerrit.getSpec().setEventsBroker(broker);

    mockGerritCluster(clusterName);

    HttpURLConnection http = sendAdmissionRequest(gerrit);

    AdmissionReview response =
        new ObjectMapper().readValue(http.getInputStream(), AdmissionReview.class);

    assertThat(http.getResponseCode(), is(equalTo(HttpServletResponse.SC_OK)));
    assertThat(response.getResponse().getAllowed(), is(true));
  }

  @Override
  protected String getCustomResource() {
    return "gerrit";
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.MULTISITE;
  }
}
