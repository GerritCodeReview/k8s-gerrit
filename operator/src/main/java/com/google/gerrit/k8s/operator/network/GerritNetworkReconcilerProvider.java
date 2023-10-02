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

package com.google.gerrit.k8s.operator.network;

import com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler;
import com.google.gerrit.k8s.operator.network.ingress.GerritIngressReconciler;
import com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler;
import com.google.gerrit.k8s.operator.network.none.GerritNoIngressReconciler;
import com.google.gerrit.k8s.operator.v1alpha.api.model.network.GerritNetwork;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class GerritNetworkReconcilerProvider implements Provider<Reconciler<GerritNetwork>> {
  private final IngressType ingressType;

  @Inject
  public GerritNetworkReconcilerProvider(@Named("IngressType") IngressType ingressType) {
    this.ingressType = ingressType;
  }

  @Override
  public Reconciler<GerritNetwork> get() {
    switch (ingressType) {
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
