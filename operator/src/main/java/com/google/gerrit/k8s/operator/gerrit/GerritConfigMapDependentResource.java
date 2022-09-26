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
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritConfigMapDiscriminator.class)
public class GerritConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {
  private static final String DEFAULT_HEALTHCHECK_CONFIG =
      "[healthcheck \"auth\"]\nenabled = false\n[healthcheck \"querychanges\"]\nenabled = false";

  public GerritConfigMapDependentResource() {
    super(ConfigMap.class);
  }

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

  protected static String getName(Gerrit gerrit) {
    return String.format("%s-configmap", gerrit.getMetadata().getName());
  }
}
