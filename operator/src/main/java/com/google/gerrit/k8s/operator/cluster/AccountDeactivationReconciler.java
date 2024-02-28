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

package com.google.gerrit.k8s.operator.cluster;

import com.google.gerrit.k8s.operator.api.model.shared.AccountDeactivationTask;
import com.google.gerrit.k8s.operator.cluster.dependent.ClusterManagedAccountDeactivationCronJob;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "account-deactivation-cronjob",
          type = ClusterManagedAccountDeactivationCronJob.class)
    })
public class AccountDeactivationReconciler implements Reconciler<AccountDeactivationTask> {
  private final KubernetesClient client;
  private ClusterManagedAccountDeactivationCronJob dependentCronJob;

  @Inject
  public AccountDeactivationReconciler(KubernetesClient client) {
    this.client = client;
    this.dependentCronJob = new ClusterManagedAccountDeactivationCronJob();
    this.dependentCronJob.setKubernetesClient(client);
  }

  @Override
  public UpdateControl<AccountDeactivationTask> reconcile(
      AccountDeactivationTask accountDeactivation, Context<AccountDeactivationTask> context) {
    return UpdateControl.noUpdate();
  }
}
