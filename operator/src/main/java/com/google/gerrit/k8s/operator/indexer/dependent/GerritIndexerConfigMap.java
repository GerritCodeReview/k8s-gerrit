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

package com.google.gerrit.k8s.operator.indexer.dependent;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritSpec;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.api.model.shared.IndexConfig;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Map;

public class GerritIndexerConfigMap
    extends CRUDReconcileAddKubernetesDependentResource<ConfigMap, GerritIndexer> {

  public GerritIndexerConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    Map<String, String> gerritIndexerLabels =
        GerritClusterLabelFactory.create(
            gerritIndexer.getMetadata().getName(),
            getName(gerritIndexer),
            this.getClass().getSimpleName());

    Map<String, String> configFiles = gerritIndexer.getSpec().getConfigFiles();

    if (!configFiles.containsKey("gerrit.config")) {
      configFiles.put("gerrit.config", "");
    }

    IndexConfig indexerIndexConfig = gerritIndexer.getSpec().getIndex();
    Gerrit primary = getPrimaryGerrit(gerritIndexer, context);
    if (indexerIndexConfig != null) {
      GerritSpec primarySpec = primary.getSpec();
      primarySpec.setIndex(indexerIndexConfig);
      primary.setSpec(primarySpec);
    }

    configFiles.put(
        "gerrit.config", new GerritConfigBuilder(gerritIndexer, primary).build().toText());

    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerritIndexer))
        .withNamespace(gerritIndexer.getMetadata().getNamespace())
        .withLabels(gerritIndexerLabels)
        .endMetadata()
        .withData(configFiles)
        .build();
  }

  public static String getName(GerritIndexer gerritIndexer) {
    return getName(gerritIndexer.getMetadata().getName());
  }

  public static String getName(String gerritIndexerName) {
    return gerritIndexerName + "-configmap";
  }

  private Gerrit getPrimaryGerrit(GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    String ns = gerritIndexer.getMetadata().getNamespace();
    KubernetesClient client = context.getClient();
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(ns)
            .withName(gerritIndexer.getSpec().getCluster())
            .get();

    // TODO: Check whether primary exists on creation
    GerritTemplate primaryGerrit =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.PRIMARY))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No primary Gerrit is part of the GerritCluster."));

    return client
        .resources(Gerrit.class)
        .inNamespace(ns)
        .withName(primaryGerrit.getMetadata().getName())
        .get();
  }
}
