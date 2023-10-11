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

package com.google.gerrit.k8s.operator.network.none;

import com.google.gerrit.k8s.operator.v1beta2.api.model.network.GerritNetwork;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Singleton
@ControllerConfiguration
public class GerritNoIngressReconciler implements Reconciler<GerritNetwork> {

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {
    return UpdateControl.noUpdate();
  }
}
