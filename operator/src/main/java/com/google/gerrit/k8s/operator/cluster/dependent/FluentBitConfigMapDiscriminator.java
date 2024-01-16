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

package com.google.gerrit.k8s.operator.cluster.dependent;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class FluentBitConfigMapDiscriminator implements ResourceDiscriminator<ConfigMap, Gerrit> {
  @Override
  public Optional<ConfigMap> distinguish(
      Class<ConfigMap> resource, Gerrit primary, Context<Gerrit> context) {
    InformerEventSource<ConfigMap, Gerrit> ies =
        (InformerEventSource<ConfigMap, Gerrit>)
            context.eventSourceRetriever().getResourceEventSourceFor(ConfigMap.class);

    return ies.get(
        new ResourceID(FluentBitConfigMap.getName(primary), primary.getMetadata().getNamespace()));
  }
}
