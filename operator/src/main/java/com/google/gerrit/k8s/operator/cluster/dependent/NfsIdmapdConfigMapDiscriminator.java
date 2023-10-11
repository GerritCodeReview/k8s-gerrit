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

import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.CM_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.v1beta2.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class NfsIdmapdConfigMapDiscriminator
    implements ResourceDiscriminator<ConfigMap, GerritCluster> {
  @Override
  public Optional<ConfigMap> distinguish(
      Class<ConfigMap> resource, GerritCluster primary, Context<GerritCluster> context) {
    InformerEventSource<ConfigMap, GerritCluster> ies =
        (InformerEventSource<ConfigMap, GerritCluster>)
            context
                .eventSourceRetriever()
                .getResourceEventSourceFor(ConfigMap.class, CM_EVENT_SOURCE);

    return ies.get(
        new ResourceID(
            NfsIdmapdConfigMap.NFS_IDMAPD_CM_NAME, primary.getMetadata().getNamespace()));
  }
}
