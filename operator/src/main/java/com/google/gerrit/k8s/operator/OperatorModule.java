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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.admission.AdmissionWebhookModule;
import com.google.gerrit.k8s.operator.cluster.GerritClusterNoSharedFSReconciler;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionReconciler;
import com.google.gerrit.k8s.operator.network.GerritNetworkReconcilerProvider;
import com.google.gerrit.k8s.operator.receiver.ReceiverReconciler;
import com.google.gerrit.k8s.operator.server.ServerModule;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class OperatorModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @SuppressWarnings("rawtypes")
  @Override
  protected void configure() {
    install(new EnvModule());
    install(new ServerModule());

    bind(KubernetesClient.class).toInstance(getKubernetesClient());
    bind(LifecycleManager.class);
    bind(GerritOperator.class);

    install(new AdmissionWebhookModule());

    Multibinder<Reconciler> reconcilers = Multibinder.newSetBinder(binder(), Reconciler.class);

    if (OperatorContext.isMultisite()) {
      reconcilers.addBinding().to(GerritClusterNoSharedFSReconciler.class);
      reconcilers.addBinding().to(GerritReconciler.class);
    } else {
      reconcilers.addBinding().to(GerritClusterReconciler.class);
      reconcilers.addBinding().to(GerritReconciler.class);
      reconcilers.addBinding().to(GitGarbageCollectionReconciler.class);
      reconcilers.addBinding().to(ReceiverReconciler.class);
      reconcilers.addBinding().toProvider(GerritNetworkReconcilerProvider.class);
    }
  }

  private KubernetesClient getKubernetesClient() {
    Config config = new ConfigBuilder().withNamespace(null).build();
    return new KubernetesClientBuilder().withConfig(config).build();
  }
}
