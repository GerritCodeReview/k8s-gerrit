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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritClusterAdmissionWebhook;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritClusterSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec;
import com.google.gerrit.k8s.operator.api.model.shared.GerritClusterIngressConfig;
import com.google.gerrit.k8s.operator.test.TestAdmissionWebhookServer;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
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

public abstract class AdmissionWebhookAbstractTest {

  protected static final String NAMESPACE = "test";
  protected TestAdmissionWebhookServer server;

  protected static final String LIST_GERRITS_PATH =
      String.format(
          "/apis/%s/namespaces/%s/%s",
          HasMetadata.getApiVersion(Gerrit.class), NAMESPACE, HasMetadata.getPlural(Gerrit.class));
  private static final String LIST_GERRIT_CLUSTERS_PATH =
      String.format(
          "/apis/%s/namespaces/%s/%s",
          HasMetadata.getApiVersion(GerritCluster.class),
          NAMESPACE,
          HasMetadata.getPlural(GerritCluster.class));

  @Rule protected KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    server = new TestAdmissionWebhookServer();

    kubernetesServer.before();

    initOperatorContext();
    server.registerWebhook(new GerritClusterAdmissionWebhook());
    server.registerWebhook(new GerritAdmissionWebhook());
    server.start();
  }

  @AfterAll
  public void shutdown() throws Exception {
    server.stop();
  }

  protected void mockGerritCluster(String name) {
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

  protected Gerrit createGerrit(String cluster, Config gerritConfig) {
    ObjectMeta meta =
        new ObjectMetaBuilder()
            .withName(RandomStringUtils.random(10))
            .withNamespace(NAMESPACE)
            .build();
    GerritSpec gerritSpec = new GerritSpec();
    gerritSpec.setMode(GerritTemplateSpec.GerritMode.PRIMARY);
    if (gerritConfig != null) {
      gerritSpec.setConfigFiles(Map.of("gerrit.config", gerritConfig.toText()));
    }
    Gerrit gerrit = new Gerrit();
    gerrit.setMetadata(meta);
    gerrit.setSpec(gerritSpec);
    return gerrit;
  }

  protected HttpURLConnection sendAdmissionRequest(CustomResource customResource)
      throws MalformedURLException, IOException {
    HttpURLConnection http =
        (HttpURLConnection)
            new URL(
                    "http://localhost:"
                        + PORT
                        + "/admission/"
                        + Constants.VERSION
                        + "/"
                        + getCustomResource())
                .openConnection();
    http.setRequestMethod(HttpMethod.POST.asString());
    http.setRequestProperty("Content-Type", "application/json");
    http.setDoOutput(true);

    AdmissionRequest admissionReq = new AdmissionRequest();
    admissionReq.setObject(customResource);
    AdmissionReview admissionReview = new AdmissionReview();
    admissionReview.setRequest(admissionReq);

    try (OutputStream os = http.getOutputStream()) {
      byte[] input = new ObjectMapper().writer().writeValueAsBytes(admissionReview);
      os.write(input, 0, input.length);
    }
    return http;
  }

  protected abstract String getCustomResource();

  protected abstract void initOperatorContext();
}
