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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.HighAvailabilityPluginConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.SpannerRefDbPluginConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.ZookeeperRefDbPluginConfigBuilder;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritConfigMapDiscriminator.class)
public class GerritConfigMap
    extends CRUDReconcileAddKubernetesDependentResource<ConfigMap, Gerrit> {
  private static final String DEFAULT_HEALTHCHECK_CONFIG =
      "[healthcheck \"auth\"]\nenabled = false\n[healthcheck \"querychanges\"]\nenabled = false";

  public GerritConfigMap() {
    super(ConfigMap.class);
  }

  @VisibleForTesting
  @Override
  public ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    Map<String, String> gerritLabels =
        GerritCluster.getLabels(
            gerrit.getMetadata().getName(), getName(gerrit), this.getClass().getSimpleName());

    Map<String, String> configFiles = gerrit.getSpec().getConfigFiles();

    if (!configFiles.containsKey("gerrit.config")) {
      configFiles.put("gerrit.config", "");
    }

    configFiles.put("gerrit.config", new GerritConfigBuilder(gerrit).build().toText());

    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      configFiles.put(
          "high-availability.config",
          new HighAvailabilityPluginConfigBuilder(gerrit).build().toText());
    }

    switch (gerrit.getSpec().getRefdb().getDatabase()) {
      case ZOOKEEPER:
        configFiles.put(
            "zookeeper-refdb.config",
            new ZookeeperRefDbPluginConfigBuilder(gerrit).build().toText());
        break;
      case SPANNER:
        configFiles.put(
            "spanner-refdb.config", new SpannerRefDbPluginConfigBuilder(gerrit).build().toText());
        break;
      default:
        break;
    }

    if (!configFiles.containsKey("healthcheck.config")) {
      configFiles.put("healthcheck.config", DEFAULT_HEALTHCHECK_CONFIG);
    }

    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(gerritLabels)
        .endMetadata()
        .withData(configFiles)
        .build();
  }

  public static String getName(Gerrit gerrit) {
    return String.format("%s-configmap", gerrit.getMetadata().getName());
  }
}
