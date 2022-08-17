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

package com.google.gerrit.k8s.operator.network;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.Gerrit;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerConfiguration(
    dependents = {@Dependent(type = GerritIngress.class, name = "gerrit-ingress")})
public class GerritNetworkReconciler
    implements Reconciler<GerritNetwork>, EventSourceInitializer<GerritNetwork> {

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritNetwork> context) {
    final SecondaryToPrimaryMapper<GerritCluster> gerritClusterMapper =
        (GerritCluster cluster) ->
            context
                .getPrimaryCache()
                .list(
                    gerritNetwork ->
                        gerritNetwork
                            .getSpec()
                            .getCluster()
                            .equals(cluster.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritCluster, GerritNetwork> gerritClusterEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritCluster.class, context)
                .withSecondaryToPrimaryMapper(gerritClusterMapper)
                .build(),
            context);

    final SecondaryToPrimaryMapper<Gerrit> gerritMapper =
        (Gerrit gerrit) ->
            context
                .getPrimaryCache()
                .list(
                    gerritNetwork ->
                        gerritNetwork.getSpec().getCluster().equals(gerrit.getSpec().getCluster()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<Gerrit, GerritNetwork> gerritEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Gerrit.class, context)
                .withSecondaryToPrimaryMapper(gerritMapper)
                .build(),
            context);

    return EventSourceInitializer.nameEventSources(gerritClusterEventSource, gerritEventSource);
  }

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    return UpdateControl.noUpdate();
  }
}
