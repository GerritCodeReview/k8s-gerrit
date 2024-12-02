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

package com.google.gerrit.k8s.operator;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.cluster.GerritClusterMultisiteReconciler;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.config.OperatorContext;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionReconciler;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler;
import com.google.gerrit.k8s.operator.network.ingress.GerritIngressReconciler;
import com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler;
import com.google.gerrit.k8s.operator.network.none.GerritNoIngressReconciler;
import com.google.gerrit.k8s.operator.receiver.ReceiverReconciler;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.IncomingReplicationTaskReconciler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class ReconcilerSetProvider implements Provider<Set<Reconciler<?>>> {
  private final KubernetesClient client;

  @Inject
  public ReconcilerSetProvider(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public Set<Reconciler<?>> get() {
    Set<Reconciler<?>> reconcilers = new HashSet<>();
    reconcilers.add(new GerritReconciler(client));
    switch (OperatorContext.getClusterMode()) {
      case MULTISITE:
        reconcilers.add(new GerritClusterMultisiteReconciler());
        reconcilers.add(new GerritIstioReconciler());
        break;
      case HIGH_AVAILABILITY:
      default:
        reconcilers.add(new GerritClusterReconciler());
        reconcilers.add(new GitGarbageCollectionReconciler(client));
        reconcilers.add(new IncomingReplicationTaskReconciler());
        reconcilers.add(new GerritIndexerReconciler());
        reconcilers.add(new ReceiverReconciler(client));
        reconcilers.add(getGerritNetworkReconciler());
    }
    return reconcilers;
  }

  @VisibleForTesting
  public static Reconciler<GerritNetwork> getGerritNetworkReconciler() {
    switch (OperatorContext.getIngressType()) {
      case INGRESS:
        return new GerritIngressReconciler();
      case ISTIO:
        return new GerritIstioReconciler();
      case AMBASSADOR:
        return new GerritAmbassadorReconciler();
      default:
        return new GerritNoIngressReconciler();
    }
  }
}
