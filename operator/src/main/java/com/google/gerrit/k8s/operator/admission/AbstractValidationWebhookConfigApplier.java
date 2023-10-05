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

package com.google.gerrit.k8s.operator.admission;

import static com.google.gerrit.k8s.operator.GerritOperator.SERVICE_NAME;
import static com.google.gerrit.k8s.operator.GerritOperator.SERVICE_PORT;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperations;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.List;

public abstract class AbstractValidationWebhookConfigApplier
    implements ValidationWebhookConfigApplier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KubernetesClient client;
  private final String namespace;
  private final KeyStoreProvider keyStoreProvider;
  private final ValidatingWebhookConfiguration cfg;

  public AbstractValidationWebhookConfigApplier(
      KubernetesClient client, String namespace, KeyStoreProvider keyStoreProvider) {
    this.client = client;
    this.namespace = namespace;
    this.keyStoreProvider = keyStoreProvider;

    this.cfg = build();
  }

  public abstract String name();

  public abstract String webhookPath();

  public abstract List<RuleWithOperations> rules();

  private String caBundle()
      throws CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException,
          CertificateException, IOException {
    return Base64.getEncoder().encodeToString(keyStoreProvider.getCertificate().getBytes());
  }

  @Override
  public ValidatingWebhookConfiguration build() {
    try {
      return new ValidatingWebhookConfigurationBuilder()
          .withNewMetadata()
          .withName(name())
          .endMetadata()
          .withWebhooks(
              new ValidatingWebhookBuilder()
                  .withName(name() + ".validator.google.com")
                  .withAdmissionReviewVersions("v1", "v1beta1")
                  .withNewClientConfig()
                  .withCaBundle(caBundle())
                  .withNewService()
                  .withName(SERVICE_NAME)
                  .withNamespace(namespace)
                  .withPath(webhookPath())
                  .withPort(SERVICE_PORT)
                  .endService()
                  .endClientConfig()
                  .withFailurePolicy("Fail")
                  .withMatchPolicy("Equivalent")
                  .withRules(rules())
                  .withTimeoutSeconds(10)
                  .withSideEffects("None")
                  .build())
          .build();
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
      throw new RuntimeException("Failed to deploy ValidationWebhookConfiguration " + name(), e);
    }
  }

  @Override
  public void apply()
      throws KeyStoreException, NoSuchProviderException, IOException, NoSuchAlgorithmException,
          CertificateException {
    logger.atInfo().log("Applying webhook config %s", cfg);
    client.resource(cfg).createOrReplace();
  }

  @Override
  public void delete() {
    logger.atInfo().log("Deleting webhook config %s", cfg);
    client.resource(cfg).delete();
  }
}
