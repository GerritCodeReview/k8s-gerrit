// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.test;

import com.google.gerrit.k8s.operator.v1beta4.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.utils.URIBuilder;

public class ReceiverUtil {
  public static final String RECEIVER_TEST_USER = "git";
  public static final String RECEIVER_TEST_PASSWORD = RandomStringUtils.randomAlphanumeric(32);
  public static final String CREDENTIALS_SECRET_NAME = "receiver-secret";
  public static final TestProperties testProps = new TestProperties();

  public static int sendReceiverApiRequest(GerritCluster gerritCluster, String method, String path)
      throws Exception {
    URL url = getReceiverUrl(gerritCluster, path);

    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    try {
      con.setRequestMethod(method);
      String encodedAuth =
          Base64.getEncoder()
              .encodeToString(
                  String.format("%s:%s", RECEIVER_TEST_USER, RECEIVER_TEST_PASSWORD)
                      .getBytes(StandardCharsets.UTF_8));
      con.setRequestProperty("Authorization", "Basic " + encodedAuth);
      return con.getResponseCode();
    } finally {
      con.disconnect();
    }
  }

  public static URL getReceiverUrl(GerritCluster gerritCluster, String path) throws Exception {
    return new URIBuilder()
        .setScheme("https")
        .setHost(gerritCluster.getSpec().getIngress().getHost())
        .setPath(path)
        .build()
        .toURL();
  }

  public static Secret createCredentialsSecret(String namespace) {
    String enPasswd = Md5Crypt.md5Crypt(RECEIVER_TEST_PASSWORD.getBytes());
    String htpasswdContent = RECEIVER_TEST_USER + ":" + enPasswd;
    return new SecretBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(CREDENTIALS_SECRET_NAME)
        .endMetadata()
        .withData(
            Map.of(".htpasswd", Base64.getEncoder().encodeToString(htpasswdContent.getBytes())))
        .build();
  }
}
