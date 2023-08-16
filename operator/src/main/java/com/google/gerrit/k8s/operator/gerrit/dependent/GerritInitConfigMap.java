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

import static com.google.gerrit.k8s.operator.cluster.model.GerritCluster.PLUGIN_CACHE_MOUNT_PATH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritInitConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritInitConfigMapDiscriminator.class)
public class GerritInitConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GerritInitConfigMap() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    Map<String, String> gerritLabels =
        GerritCluster.getLabels(
            gerrit.getMetadata().getName(), getName(gerrit), this.getClass().getSimpleName());

    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(gerritLabels)
        .endMetadata()
        .withData(Map.of("gerrit-init.yaml", getGerritInitConfig(gerrit)))
        .build();
  }

  private String getGerritInitConfig(Gerrit gerrit) {
    GerritInitConfig config = new GerritInitConfig();
    config.setPlugins(gerrit.getSpec().getPlugins());
    config.setPluginCacheEnabled(gerrit.getSpec().getStorage().getPluginCache().isEnabled());
    config.setPluginCacheDir(PLUGIN_CACHE_MOUNT_PATH);

    ObjectMapper mapper =
        new ObjectMapper(new YAMLFactory().disable(Feature.WRITE_DOC_START_MARKER));
    try {
      return mapper.writeValueAsString(config);
    } catch (JsonProcessingException e) {
      logger.atSevere().withCause(e).log("Could not serialize gerrit-init.config");
      throw new IllegalStateException(e);
    }
  }

  public static String getName(Gerrit gerrit) {
    return String.format("%s-init-configmap", gerrit.getMetadata().getName());
  }
}
