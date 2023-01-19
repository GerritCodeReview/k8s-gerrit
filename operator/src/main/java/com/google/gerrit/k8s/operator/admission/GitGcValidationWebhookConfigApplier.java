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

package com.google.gerrit.k8s.operator.admission;

import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperations;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperationsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

@Singleton
public class GitGcValidationWebhookConfigApplier extends AbstractValidationWebhookConfigApplier {

  @Inject
  public GitGcValidationWebhookConfigApplier(
      KubernetesClient client,
      @Named("Namespace") String namespace,
      KeyStoreProvider keyStoreProvider) {
    super(client, namespace, keyStoreProvider);
  }

  @Override
  String name() {
    return "gitgc";
  }

  @Override
  String webhookPath() {
    return "/admission/gitgc";
  }

  @Override
  List<RuleWithOperations> rules() {
    return List.of(
        new RuleWithOperationsBuilder()
            .withApiGroups("gerritoperator.google.com")
            .withApiVersions("*")
            .withOperations("CREATE", "UPDATE")
            .withResources("gitgcs")
            .withScope("*")
            .build());
  }
}
