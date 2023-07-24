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

import com.google.gerrit.k8s.operator.network.ingress.GerritIngressReconciler;
import com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class NetworkModule extends AbstractModule {

  @Override
  public void configure() {
    IngressType ingressType = IngressType.valueOf(System.getenv("INGRESS_TYPE"));
    bind(IngressType.class).annotatedWith(Names.named("IngressType")).toInstance(ingressType);

    switch (ingressType) {
      case INGRESS:
        bind(GerritNetworkReconciler.class).to(GerritIngressReconciler.class);
        break;
      case ISTIO:
        bind(GerritNetworkReconciler.class).to(GerritIstioReconciler.class);
        break;
      default:
        break;
    }
  }
}
