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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.model.NfsWorkaroundConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class NfsWorkaroundCondition implements Condition<ConfigMap, GerritCluster> {

  @Override
  public boolean isMet(
      GerritCluster gerritCluster, ConfigMap secondary, Context<GerritCluster> context) {
    NfsWorkaroundConfig cfg = gerritCluster.getSpec().getStorageClasses().getNfsWorkaround();
    return cfg.isEnabled() && cfg.getIdmapdConfig() != null;
  }
}
