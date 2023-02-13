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

package com.google.gerrit.k8s.operator.admission;

import com.google.gerrit.k8s.operator.LifecycleManager;
import com.google.inject.Inject;
import java.util.Set;

public class ValidationWebhookConfigs {

  private final Set<ValidationWebhookConfigApplier> configAppliers;

  @Inject
  public ValidationWebhookConfigs(
      LifecycleManager lifecycleManager, Set<ValidationWebhookConfigApplier> configAppliers) {
    this.configAppliers = configAppliers;
    lifecycleManager.addShutdownHook(
        new Runnable() {

          @Override
          public void run() {
            delete();
          }
        });
  }

  public void apply() throws Exception {
    for (ValidationWebhookConfigApplier applier : configAppliers) {
      applier.apply();
    }
  }

  public void delete() {
    for (ValidationWebhookConfigApplier applier : configAppliers) {
      applier.delete();
    }
  }
}
