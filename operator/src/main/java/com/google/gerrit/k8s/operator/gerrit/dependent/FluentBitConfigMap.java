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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = FluentBitConfigMapDiscriminator.class)
public class FluentBitConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Gerrit> {
  private static final String FLUENT_BIT_CONFIG =
      """
      [SERVICE]
        Parsers_file parsers-multiline.conf
      [INPUT]
        Name tail
        Path /var/mnt/logs/*log
        Tag <log_name>
        Tag_Regex ^\\/var\\/mnt\\/logs\\/(?<log_name>[^*]+)
        Multiline.parser gerrit-multiline
        Buffer_Chunk_Size 10M
        Buffer_Max_Size 10M\n
      """;
  private static final String MULTILINE_PARSER_CONFIG =
      """
      [MULTILINE_PARSER]
        Name gerrit-multiline
        Type regex
        Flush_timeout 1000
        Rule "start_state" "/\\[\\d{4}-\\d{2}-\\d{2}(.*?)\\](.*)/" "cont"
        Rule "cont" "^(?!\\[)(.*)" "cont"
      """;

  public FluentBitConfigMap() {
    super(ConfigMap.class);
  }

  public static String getName(Gerrit gerrit) {
    return String.format("%s-fluentbit-configmap", gerrit.getMetadata().getName());
  }

  @Override
  protected ConfigMap desired(Gerrit gerrit, Context<Gerrit> context) {
    String customConfig = gerrit.getSpec().getFluentBitSidecar().getConfig();
    String config = FLUENT_BIT_CONFIG + customConfig;

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
            Map.ofEntries(
                Map.entry("fluent-bit.conf", config),
                Map.entry("parsers-multiline.conf", MULTILINE_PARSER_CONFIG)))
        .build();
  }
}
