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

package com.google.gerrit.k8s.operator.v1beta2.admission;

import com.google.gerrit.k8s.operator.admission.AbstractValidationWebhookConfigApplier;
import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperations;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperationsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

@Singleton
public class GerritClusterValidationWebhookConfigApplier
    extends AbstractValidationWebhookConfigApplier {

  @Inject
  public GerritClusterValidationWebhookConfigApplier(
      KubernetesClient client,
      @Named("Namespace") String namespace,
      KeyStoreProvider keyStoreProvider) {
    super(client, namespace, keyStoreProvider);
  }

  @Override
  public String name() {
    return "gerrit-cluster";
  }

  @Override
  public String webhookPath() {
    return "/admission/v1beta1/gerritcluster";
  }

  @Override
  public List<RuleWithOperations> rules() {
    return List.of(
        new RuleWithOperationsBuilder()
            .withApiGroups("gerritoperator.google.com")
            .withApiVersions("v1beta1")
            .withOperations("CREATE", "UPDATE")
            .withResources("gerritclusters")
            .withScope("*")
            .build());
  }
}
