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

package com.google.gerrit.k8s.operator.indexer;

import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritIndexerConfigMap;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritIndexerJob;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Singleton
@Workflow(
    dependents = {
      @Dependent(name = "gerrit-indexer-configmap", type = GerritIndexerConfigMap.class),
      @Dependent(
          name = "gerrit-indexer-job",
          type = GerritIndexerJob.class,
          dependsOn = "gerrit-indexer-configmap")
    })
public class GerritIndexerReconciler implements Reconciler<GerritIndexer> {

  @Override
  public UpdateControl<GerritIndexer> reconcile(
      GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    return UpdateControl.noUpdate();
  }
}
