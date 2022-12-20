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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.cluster.GerritIngress;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritPluginConfigMapDiscriminator.class)
public class GerritPluginConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {
  public GerritPluginConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(gerrit.getSpec().getCluster())
            .get();
    if (gerritCluster == null) {
      throw new IllegalStateException("The Gerrit cluster could not be found.");
    }

    Map<String, String> gerritLabels =
        gerritCluster.getLabels(getName(gerrit), this.getClass().getSimpleName());

    Map<String, String> configFiles = gerrit.getSpec().getConfigFiles();

    if (configFiles.containsKey("gerrit.config")) {
      configFiles.remove("gerrit.config");
    }
    if (configFiles.containsKey("healthcheck.config")) {
      configFiles.remove("healthcheck.config");
    }

    for (var entry : configFiles.entrySet()) {
      GerritConfigBuilder gerritConfigBuilder =
          new GerritConfigBuilder().withConfig(configFiles.get(entry.getKey()));

      if (gerritCluster.getSpec().getIngress().isEnabled()) {
        gerritConfigBuilder.withUrl(
            GerritIngress.getFullHostname(ServiceDependentResource.getName(gerrit), gerritCluster));
      } else {
        gerritConfigBuilder.withUrl(ServiceDependentResource.getHostname(gerrit));
      }

      configFiles.put(entry.getKey(), gerritConfigBuilder.build().toText());
    }

    logger.atInfo().log("Plugins loaded: %s", configFiles.keySet().toString());

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

  protected static String getName(Gerrit gerrit) {
    return String.format("%s-plugin-configmap", gerrit.getMetadata().getName());
  }
}
