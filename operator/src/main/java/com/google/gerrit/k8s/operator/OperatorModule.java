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

package com.google.gerrit.k8s.operator;

import com.google.gerrit.k8s.operator.admission.AdmissionWebhookModule;
import com.google.gerrit.k8s.operator.server.ServerModule;
import com.google.inject.AbstractModule;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class OperatorModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new EnvModule());
    install(new ServerModule());

    KubernetesClient client = getKubernetesClient();

    bind(KubernetesClient.class).toInstance(client);
    bind(LifecycleManager.class);
    bind(GerritOperator.class);

    bind(ReconcilerSetProvider.class);

    install(new AdmissionWebhookModule());
  }

  private KubernetesClient getKubernetesClient() {
    Config config = new ConfigBuilder().withNamespace(null).build();
    return new KubernetesClientBuilder().withConfig(config).build();
  }
}
