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
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritAdmissionWebhookTest extends AdmissionWebhookAbstractTest {

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

  @Override
  protected String getCustomResource() {
    return Constants.GERRIT_KIND;
  }
}
