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

package com.google.gerrit.k8s.operator.config;

import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.config.dependent.GerritOperatorService;
import com.google.gerrit.k8s.operator.config.dependent.GerritOperatorVWCCondition;
import com.google.gerrit.k8s.operator.config.dependent.ValidatingWebhookConfigurations;
import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import com.google.inject.Inject;
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
          name = "operator-service",
          type = GerritOperatorService.class,
          reconcilePrecondition = GerritOperatorVWCCondition.class),
      @Dependent(
          name = "validating-webhook-configurations",
          type = ValidatingWebhookConfigurations.class,
          reconcilePrecondition = GerritOperatorVWCCondition.class),
    })
public class GerritOperatorConfigReconciler implements Reconciler<GerritOperatorConfig> {

  @Inject
  public GerritOperatorConfigReconciler(KeyStoreProvider keyStoreProvider) {
    ValidatingWebhookConfigurations.setKeyStoreProvider(keyStoreProvider);
  }

  @Override
  public UpdateControl<GerritOperatorConfig> reconcile(
      GerritOperatorConfig gerritOperatorConfig, Context<GerritOperatorConfig> context) {
    return UpdateControl.noUpdate();
  }
}
