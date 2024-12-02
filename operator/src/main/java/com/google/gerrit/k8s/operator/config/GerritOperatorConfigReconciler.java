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

package com.google.gerrit.k8s.operator.config;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.GerritOperator;
import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;

@Singleton
@ControllerConfiguration()
public class GerritOperatorConfigReconciler
    implements Reconciler<GerritOperatorConfig>, EventSourceInitializer<GerritOperatorConfig> {
  public static final String OPERATOR_DEPLOYMENT_EVENT_SOURCE = "operator-deployment-event-source";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GerritOperator operator;

  @Inject
  public GerritOperatorConfigReconciler(GerritOperator operator) {
    this.operator = operator;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GerritOperatorConfig> context) {
    Map<String, EventSource> eventSources = new HashMap<>();
    InformerEventSource<Deployment, GerritOperatorConfig> operatorDeploymentEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Deployment.class, context).build(), context);
    eventSources.put(OPERATOR_DEPLOYMENT_EVENT_SOURCE, operatorDeploymentEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritOperatorConfig> reconcile(
      GerritOperatorConfig config, Context<GerritOperatorConfig> context) throws Exception {
    OperatorContext.createInstance(
        config.getSpec().getClusterMode(), config.getSpec().getIngressType());
    logger.atInfo().log("Applied configuration: %s", OperatorContext.print());
    logger.atInfo().log("Restarting Operator");
    operator.restart();
    return UpdateControl.noUpdate();
  }
}
