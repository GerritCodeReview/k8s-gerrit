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
import com.google.gerrit.k8s.operator.cluster.GerritClusterMemberDependentResource;
import com.google.gerrit.k8s.operator.cluster.GerritIngressConfig.IngressType;
import com.google.gerrit.k8s.operator.gerrit.GerritSpec.GerritMode;
import com.google.gerrit.k8s.operator.gerrit.config.GerritConfigBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = GerritConfigMapDiscriminator.class)
public class GerritConfigMapDependentResource
    extends GerritClusterMemberDependentResource<ConfigMap, Gerrit> {
  private static final String DEFAULT_HEALTHCHECK_CONFIG =
      "[healthcheck \"auth\"]\nenabled = false\n[healthcheck \"querychanges\"]\nenabled = false";

  public GerritConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    GerritCluster gerritCluster = getGerritCluster(gerrit);
    Map<String, String> gerritLabels =
        gerritCluster.getLabels(getName(gerrit), this.getClass().getSimpleName());

    Map<String, String> configFiles = gerrit.getSpec().getConfigFiles();

    if (!configFiles.containsKey("gerrit.config")) {
      configFiles.put("gerrit.config", "");
    }

    GerritConfigBuilder gerritConfigBuilder =
        new GerritConfigBuilder().withConfig(configFiles.get("gerrit.config"));

    gerritConfigBuilder.useReplicaMode(gerrit.getSpec().getMode().equals(GerritMode.REPLICA));

    if (gerritCluster.getSpec().getIngress().isEnabled()) {
      gerritConfigBuilder.withUrl(
          gerritCluster.getSpec().getIngress().getUrl(ServiceDependentResource.getName(gerrit)));
    } else {
      gerritConfigBuilder.withUrl(ServiceDependentResource.getUrl(gerrit));
    }

    if (gerritCluster.getSpec().getIngress().getType() == IngressType.ISTIO) {
      gerritConfigBuilder.withSsh(
          gerrit.getSpec().getService().isSshEnabled(),
          gerritCluster
                  .getSpec()
                  .getIngress()
                  .getFullHostnameForService(ServiceDependentResource.getName(gerrit))
              + ":29418");
    } else {
      gerritConfigBuilder.withSsh(gerrit.getSpec().getService().isSshEnabled());
    }

    configFiles.put("gerrit.config", gerritConfigBuilder.build().toText());

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
