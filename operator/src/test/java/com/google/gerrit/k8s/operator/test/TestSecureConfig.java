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

package com.google.gerrit.k8s.operator.test;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Base64;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

public class TestSecureConfig {
  public static final String SECURE_CONFIG_SECRET_NAME = "gerrit-secret";

  private final KubernetesClient client;
  private final String namespace;

  private Config secureConfig = new Config();
  private Secret secureConfigSecret;

  public TestSecureConfig(KubernetesClient client, TestProperties testProps, String namespace) {
    this.client = client;
    this.namespace = namespace;
    this.secureConfig.setString("ldap", null, "password", testProps.getLdapAdminPwd());
  }

  public void createOrReplace() {
    secureConfigSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(SECURE_CONFIG_SECRET_NAME)
            .endMetadata()
            .withData(
                Map.of(
                    "secure.config",
                    Base64.getEncoder().encodeToString(secureConfig.toText().getBytes())))
            .build();
    client.resource(secureConfigSecret).inNamespace(namespace).createOrReplace();
  }

  public void modify(String section, String key, String value) {
    secureConfig.setString(section, null, key, value);
    createOrReplace();
  }
}
