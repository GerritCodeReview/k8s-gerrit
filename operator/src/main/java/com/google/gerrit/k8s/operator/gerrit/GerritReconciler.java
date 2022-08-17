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

package com.google.gerrit.k8s.operator.gerrit;

import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ControllerConfiguration(
    dependents = {
      @Dependent(name = "gerrit-configmap", type = GerritConfigMapDependentResource.class),
      @Dependent(name = "gerrit-init-configmap", type = GerritInitConfigMapDependentResource.class),
      @Dependent(
          name = "gerrit-statefulset",
          type = StatefulSetDependentResource.class,
          dependsOn = {"gerrit-configmap", "gerrit-init-configmap"}),
      @Dependent(
          name = "gerrit-service",
          type = ServiceDependentResource.class,
          dependsOn = {"gerrit-statefulset"})
    })
public class GerritReconciler implements Reconciler<Gerrit>, EventSourceInitializer<Gerrit> {

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Gerrit> context) {
    final SecondaryToPrimaryMapper<GerritCluster> gerritClusterMapper =
        (GerritCluster cluster) ->
            context
                .getPrimaryCache()
                .list(
                    gerrit -> gerrit.getSpec().getCluster().equals(cluster.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<GerritCluster, Gerrit> gerritClusterEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(GerritCluster.class, context)
                .withSecondaryToPrimaryMapper(gerritClusterMapper)
                .build(),
            context);

    return EventSourceInitializer.nameEventSources(gerritClusterEventSource);
  }

  @Override
  public UpdateControl<Gerrit> reconcile(Gerrit gerrit, Context<Gerrit> context) throws Exception {
    GerritStatus status = gerrit.getStatus();
    if (status == null) {
      status = new GerritStatus();
    }
    Optional<WorkflowReconcileResult> result =
        context.managedDependentResourceContext().getWorkflowReconcileResult();
    if (result.isPresent()) {
      status.setReady(result.get().allDependentResourcesReady());
    } else {
      status.setReady(false);
    }
    gerrit.setStatus(status);
    return UpdateControl.patchStatus(gerrit);
  }
}
