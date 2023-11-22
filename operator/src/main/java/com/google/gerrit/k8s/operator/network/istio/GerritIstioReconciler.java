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

package com.google.gerrit.k8s.operator.network.istio;

import static com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler.ISTIO_DESTINATION_RULE_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler.ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.network.GerritClusterIngressCondition;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritClusterIstioGateway;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioCondition;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioDestinationRule;
import com.google.gerrit.k8s.operator.network.istio.dependent.GerritIstioVirtualService;
import com.google.gerrit.k8s.operator.v1beta2.api.model.network.GerritNetwork;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
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
          name = "gerrit-destination-rules",
          type = GerritIstioDestinationRule.class,
          reconcilePrecondition = GerritIstioCondition.class,
          useEventSourceWithName = ISTIO_DESTINATION_RULE_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-istio-gateway",
          type = GerritClusterIstioGateway.class,
          reconcilePrecondition = GerritClusterIngressCondition.class),
      @Dependent(
          name = "gerrit-istio-virtual-service",
          type = GerritIstioVirtualService.class,
          reconcilePrecondition = GerritIstioCondition.class,
          dependsOn = {"gerrit-istio-gateway"},
          useEventSourceWithName = ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE)
    })
public class GerritIstioReconciler
    implements Reconciler<GerritNetwork>, EventSourceInitializer<GerritNetwork> {
  public static final String ISTIO_DESTINATION_RULE_EVENT_SOURCE =
      "gerrit-cluster-istio-destination-rule";
  public static final String ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE =
      "gerrit-cluster-istio-virtual-service";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritNetwork> context) {
    InformerEventSource<DestinationRule, GerritNetwork> gerritIstioDestinationRuleEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(DestinationRule.class, context).build(), context);

    InformerEventSource<VirtualService, GerritNetwork> virtualServiceEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(VirtualService.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(ISTIO_DESTINATION_RULE_EVENT_SOURCE, gerritIstioDestinationRuleEventSource);
    eventSources.put(ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE, virtualServiceEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {
    return UpdateControl.noUpdate();
  }
}
