// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.TCP_MAPPING_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterTCPMappingSsh.GERRIT_TCP_MAPPING_SSH;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import io.getambassador.v2.TCPMapping;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class GerritClusterTCPMappingSshDiscriminator
    implements ResourceDiscriminator<TCPMapping, GerritNetwork> {
  @Override
  public Optional<TCPMapping> distinguish(
      Class<TCPMapping> resource, GerritNetwork network, Context<GerritNetwork> context) {
    InformerEventSource<TCPMapping, GerritNetwork> ies =
        (InformerEventSource<TCPMapping, GerritNetwork>)
            context
                .eventSourceRetriever()
                .getResourceEventSourceFor(TCPMapping.class, TCP_MAPPING_EVENT_SOURCE);
    return ies.get(new ResourceID(GERRIT_TCP_MAPPING_SSH, network.getMetadata().getNamespace()));
  }
}
