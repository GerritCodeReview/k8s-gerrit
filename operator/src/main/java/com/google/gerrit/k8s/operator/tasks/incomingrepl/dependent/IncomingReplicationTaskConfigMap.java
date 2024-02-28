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

package com.google.gerrit.k8s.operator.tasks.incomingrepl.dependent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTask;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

@KubernetesDependent
public class IncomingReplicationTaskConfigMap
    extends CRUDReconcileAddKubernetesDependentResource<ConfigMap, IncomingReplicationTask> {
  public static final String CONFIG_FILE_NAME = "incoming-replication.config.yaml";

  public IncomingReplicationTaskConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  public ConfigMap desired(
      IncomingReplicationTask incomingReplTask, Context<IncomingReplicationTask> context) {
    Map<String, String> labels =
        GerritCluster.getLabels(
            incomingReplTask.getMetadata().getName(),
            getName(incomingReplTask),
            this.getClass().getSimpleName());

    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(incomingReplTask))
        .withNamespace(incomingReplTask.getMetadata().getNamespace())
        .withLabels(labels)
        .endMetadata()
        .withData(Map.of(CONFIG_FILE_NAME, getConfigAsString(incomingReplTask)))
        .build();
  }

  public static String getName(IncomingReplicationTask incomingReplTask) {
    return String.format("%s-configmap", incomingReplTask.getMetadata().getName());
  }

  private String getConfigAsString(IncomingReplicationTask incomingReplTask) {
    StringWriter writer = new StringWriter();
    try {
      new ObjectMapper(new YAMLFactory())
          .writeValue(writer, incomingReplTask.getSpec().getConfig());
    } catch (IOException e) {
      throw new OperatorException(
          "Failed to write configuration for incoming replication task.", e);
    }
    return writer.toString();
  }
}
