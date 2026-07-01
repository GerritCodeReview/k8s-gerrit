// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.config.dependent;

import static com.google.gerrit.k8s.operator.Constants.RESOURCES_WITH_VALIDATING_WEBHOOK;
import static com.google.gerrit.k8s.operator.Constants.VERSION;
import static com.google.gerrit.k8s.operator.config.dependent.GerritOperatorService.SERVICE_NAME;
import static com.google.gerrit.k8s.operator.config.dependent.GerritOperatorService.SERVICE_PORT;

import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperations;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperationsBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhook;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ValidatingWebhookConfigurations
    extends KubernetesDependentResource<ValidatingWebhookConfiguration, GerritOperatorConfig>
    implements Creator<ValidatingWebhookConfiguration, GerritOperatorConfig>,
        Updater<ValidatingWebhookConfiguration, GerritOperatorConfig>,
        BulkDependentResource<ValidatingWebhookConfiguration, GerritOperatorConfig, ResourceID> {

  private static KeyStoreProvider keyStoreProvider;

  /** Called once at operator startup (via Guice) before any reconciliation. */
  public static void setKeyStoreProvider(KeyStoreProvider provider) {
    keyStoreProvider = provider;
  }

  public ValidatingWebhookConfigurations() {
    super(ValidatingWebhookConfiguration.class);
  }

  @Override
  public Map<ResourceID, ValidatingWebhookConfiguration> desiredResources(
      GerritOperatorConfig operatorConfig, Context<GerritOperatorConfig> context) {
    Map<ResourceID, ValidatingWebhookConfiguration> webhookConfigs = new HashMap<>();
    if (operatorConfig.getSpec().isEnableValidatingWebhook()) {
      for (String customResourceName : RESOURCES_WITH_VALIDATING_WEBHOOK) {
        ValidatingWebhookConfiguration vwc = desired(operatorConfig, customResourceName);
        webhookConfigs.put(ResourceID.fromResource(vwc), vwc);
      }
    }
    return webhookConfigs;
  }

  private ValidatingWebhookConfiguration desired(
      GerritOperatorConfig operatorConfig, String customResourceName) {
    List<RuleWithOperations> rules =
        List.of(
            new RuleWithOperationsBuilder()
                .withApiGroups("gerritoperator.google.com")
                .withApiVersions(VERSION)
                .withOperations("CREATE", "UPDATE")
                .withResources(customResourceName)
                .withScope("*")
                .build());

    String caBundle;
    try {
      caBundle = Base64.getEncoder().encodeToString(keyStoreProvider.getCertificate().getBytes());
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
      throw new IllegalStateException(
          "Failed to get CA Bundle for ValidatingWebhookConfiguration.", e);
    }

    ValidatingWebhook webhook =
        new ValidatingWebhookBuilder()
            .withName(customResourceName.toLowerCase() + "." + VERSION + ".validator.google.com")
            .withAdmissionReviewVersions("v1", "v1beta1")
            .withNewClientConfig()
            .withCaBundle(caBundle)
            .withNewService()
            .withName(SERVICE_NAME)
            .withNamespace(operatorConfig.getMetadata().getNamespace())
            .withPath(String.format("/admission/%s/%s", VERSION, customResourceName).toLowerCase())
            .withPort(SERVICE_PORT)
            .endService()
            .endClientConfig()
            .withFailurePolicy("Fail")
            .withMatchPolicy("Equivalent")
            .withRules(rules)
            .withTimeoutSeconds(10)
            .withSideEffects("None")
            .build();

    return new ValidatingWebhookConfigurationBuilder()
        .withNewMetadata()
        .withName(customResourceName.toLowerCase())
        .endMetadata()
        .withWebhooks(webhook)
        .build();
  }

  @Override
  public Matcher.Result<ValidatingWebhookConfiguration> match(
      ValidatingWebhookConfiguration actual,
      ValidatingWebhookConfiguration desired,
      GerritOperatorConfig primary,
      Context<GerritOperatorConfig> context) {
    // Delegate to GenericKubernetesResourceMatcher for the standard structural comparison.
    Matcher.Result<ValidatingWebhookConfiguration> baseResult =
        super.match(actual, desired, primary, context);
    if (!baseResult.matched()) {
      return baseResult;
    }

    // GenericKubernetesResourceMatcher only covers spec/metadata and would miss any
    // changes to the top-level webhooks field (e.g. caBundle on keystore rotation).
    return Matcher.Result.nonComputed(Objects.equals(actual.getWebhooks(), desired.getWebhooks()));
  }

  @Override
  public Map<ResourceID, ValidatingWebhookConfiguration> getSecondaryResources(
      GerritOperatorConfig operatorConfig, Context<GerritOperatorConfig> context) {
    Set<ValidatingWebhookConfiguration> vwcs =
        context.getSecondaryResources(ValidatingWebhookConfiguration.class);
    Map<ResourceID, ValidatingWebhookConfiguration> result = new HashMap<>(vwcs.size());
    for (ValidatingWebhookConfiguration vwc : vwcs) {
      result.put(ResourceID.fromResource(vwc), vwc);
    }
    return result;
  }
}
