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

package com.google.gerrit.k8s.operator;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import java.util.Set;

@Singleton
public class GerritOperator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String SERVICE_NAME = "gerrit-operator";
  public static final int SERVICE_PORT = 8080;

  private final KubernetesClient client;

  @SuppressWarnings("rawtypes")
  private final Set<Reconciler> reconcilers;

  private Operator operator;

  @Inject
  @SuppressWarnings("rawtypes")
  public GerritOperator(
      KubernetesClient client,
      Set<Reconciler> reconcilers) {
    this.client = client;
    this.reconcilers = reconcilers;
  }

  public void start() throws Exception {
    operator =
        new Operator(
            overrider ->
                overrider
                    .withSSABasedCreateUpdateMatchForDependentResources(false)
                    .withKubernetesClient(client));
    for (Reconciler<?> reconciler : reconcilers) {
      logger.atInfo().log(
          String.format("Registering reconciler: %s", reconciler.getClass().getSimpleName()));
      operator.register(reconciler);
    }
    operator.start();
  }
}
