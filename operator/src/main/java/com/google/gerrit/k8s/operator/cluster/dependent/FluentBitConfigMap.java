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

import com.google.gerrit.k8s.operator.v1beta2.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = FluentBitConfigMapDiscriminator.class)
public class FluentBitConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {

  public FluentBitConfigMap() {
    super(ConfigMap.class);
  }

  public static String getName(Gerrit gerrit) {
    return String.format("%s-fluentbit-configmap", gerrit.getMetadata().getName());
  }

  @Override
  protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    String host = gerrit.getSpec().getFluentBitSidecar().getOutputHost();
    int port = gerrit.getSpec().getFluentBitSidecar().getOutputPort();
    return new ConfigMapBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(
            GerritCluster.getLabels(
                gerrit.getMetadata().getName(), getName(gerrit), this.getClass().getSimpleName()))
        .endMetadata()
        .withData(
            Map.of(
                "fluent-bit.conf",
                String.format(
                    "[SERVICE]"
                        + "\n\tFlush             5"
                        + "\n\tDaemon            off"
                        + "\n\tLog_Level         info"
                        + "\n[INPUT]"
                        + "\n\tName              tail"
                        + "\n\tPath              /var/mnt/logs/*log"
                        + "\n[OUTPUT]"
                        + "\n\tName              stdout"
                        + "\n\tMatch             *"
                        + "\n[OUTPUT]"
                        + "\n\tName              forward"
                        + "\n\tMatch             *"
                        + "\n\tHost              %s"
                        + "\n\tPort              %d",
                    host, port)))
        .build();
  }
}
