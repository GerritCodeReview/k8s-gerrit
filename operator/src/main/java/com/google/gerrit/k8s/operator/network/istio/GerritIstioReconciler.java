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

package com.google.gerrit.k8s.operator.network.istio;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.network.GerritClusterIngressCondition;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritClusterIstioGateway;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioDestinationRule;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioServiceEntries;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioVirtualService;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Singleton
@Workflow(
    dependents = {
      @Dependent(
          name = "gerrit-destination-rules",
          type = GerritIstioDestinationRule.class,
          reconcilePrecondition = GerritClusterIngressCondition.class),
      @Dependent(
          name = "gerrit-service-entries",
          type = GerritIstioServiceEntries.class,
          reconcilePrecondition = GerritClusterIngressCondition.class),
      @Dependent(
          name = "gerrit-istio-gateway",
          type = GerritClusterIstioGateway.class,
          reconcilePrecondition = GerritClusterIngressCondition.class),
      @Dependent(
          name = "gerrit-istio-virtual-service",
          type = GerritIstioVirtualService.class,
          reconcilePrecondition = GerritClusterIngressCondition.class,
          dependsOn = {"gerrit-istio-gateway"})
    })
public class GerritIstioReconciler implements Reconciler<GerritNetwork> {

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {
    return UpdateControl.noUpdate();
  }
}
