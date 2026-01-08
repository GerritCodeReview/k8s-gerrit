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

import static com.google.gerrit.k8s.operator.config.GerritOperatorConfigReconciler.SVC_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.api.model.config.GerritOperatorConfig;
import com.google.gerrit.k8s.operator.config.dependent.GerritOperatorService;
import com.google.gerrit.k8s.operator.config.dependent.GerritOperatorServiceCondition;
import com.google.gerrit.k8s.operator.config.dependent.ValidatingWebhookConfigurations;
import com.google.gerrit.k8s.operator.server.KeyStoreProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "operator-service",
          type = GerritOperatorService.class,
          reconcilePrecondition = GerritOperatorServiceCondition.class,
          useEventSourceWithName = SVC_EVENT_SOURCE),
    })
public class GerritOperatorConfigReconciler
    implements Reconciler<GerritOperatorConfig>, EventSourceInitializer<GerritOperatorConfig> {
  public static final String SVC_EVENT_SOURCE = "operator-svc-event-source";
  public static final String VWC_EVENT_SOURCE = "vwc-event-source";

  private final ValidatingWebhookConfigurations dependentVWCs;

  @Inject
  public GerritOperatorConfigReconciler(KeyStoreProvider keyStoreProvider) {
    this.dependentVWCs = new ValidatingWebhookConfigurations(keyStoreProvider);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GerritOperatorConfig> context) {
    InformerEventSource<Service, GerritOperatorConfig> svcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Service.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(SVC_EVENT_SOURCE, svcEventSource);
    eventSources.put(VWC_EVENT_SOURCE, dependentVWCs.initEventSource(context));
    return eventSources;
  }

  @Override
  public UpdateControl<GerritOperatorConfig> reconcile(
      GerritOperatorConfig gerritOperatorConfig, Context<GerritOperatorConfig> context) {
    dependentVWCs.reconcile(gerritOperatorConfig, context);
    return UpdateControl.noUpdate();
  }
}
