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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTaskTemplate;
import com.google.gerrit.k8s.operator.util.KubernetesDependentCustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@KubernetesDependent
public class ClusterManagedIncomingReplicationTask
    extends KubernetesDependentCustomResource<IncomingReplicationTask, GerritCluster>
    implements Deleter<GerritCluster>,
        BulkDependentResource<IncomingReplicationTask, GerritCluster> {

  public ClusterManagedIncomingReplicationTask() {
    super(IncomingReplicationTask.class);
  }

  @Override
  public Map<String, IncomingReplicationTask> desiredResources(
      GerritCluster primary, Context<GerritCluster> context) {
    Map<String, IncomingReplicationTask> incomingReplTasks = new HashMap<>();
    for (IncomingReplicationTaskTemplate incomingReplTaskTemplate :
        primary.getSpec().getScheduledTasks().getIncomingReplication()) {
      IncomingReplicationTask incomingReplTask =
          incomingReplTaskTemplate.toIncomingReplicationTask(primary);
      incomingReplTasks.put(incomingReplTask.getMetadata().getName(), incomingReplTask);
    }
    return incomingReplTasks;
  }

  @Override
  public Map<String, IncomingReplicationTask> getSecondaryResources(
      GerritCluster primary, Context<GerritCluster> context) {
    Set<IncomingReplicationTask> incomingReplTasks =
        context.getSecondaryResources(IncomingReplicationTask.class);
    Map<String, IncomingReplicationTask> result = new HashMap<>(incomingReplTasks.size());
    for (IncomingReplicationTask incomingReplTask : incomingReplTasks) {
      result.put(incomingReplTask.getMetadata().getName(), incomingReplTask);
    }
    return result;
  }
}
