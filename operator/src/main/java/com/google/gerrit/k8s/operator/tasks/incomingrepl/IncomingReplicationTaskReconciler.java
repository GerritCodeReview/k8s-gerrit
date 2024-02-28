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

package com.google.gerrit.k8s.operator.tasks.incomingrepl;

import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent.IncomingReplicationTaskConfigMap;
import com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent.IncomingReplicationTaskCronJob;
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
          name = "incoming-repl-task-configmap",
          type = IncomingReplicationTaskConfigMap.class),
      @Dependent(
          name = "incoming-repl-task-cronjob",
          type = IncomingReplicationTaskCronJob.class,
          dependsOn = "incoming-repl-task-configmap")
    })
public class IncomingReplicationTaskReconciler implements Reconciler<IncomingReplicationTask> {

  @Override
  public UpdateControl<IncomingReplicationTask> reconcile(
      IncomingReplicationTask incomingRepl, Context<IncomingReplicationTask> context) {
    return UpdateControl.noUpdate();
  }
}
