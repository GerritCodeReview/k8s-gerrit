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

package com.google.gerrit.k8s.operator.network.istio.dependent;

import static com.google.gerrit.k8s.operator.network.istio.GerritIstioReconciler.ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class GerritIstioVirtualServiceDiscriminator
    implements ResourceDiscriminator<VirtualService, GerritNetwork> {
  @Override
  public Optional<VirtualService> distinguish(
      Class<VirtualService> resource, GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    InformerEventSource<VirtualService, GerritNetwork> ies =
        (InformerEventSource<VirtualService, GerritNetwork>)
            context
                .eventSourceRetriever()
                .getResourceEventSourceFor(
                    VirtualService.class, ISTIO_VIRTUAL_SERVICE_EVENT_SOURCE);

    return ies.get(
        new ResourceID(
            gerritNetwork.getDependentResourceName(GerritIstioVirtualService.NAME_SUFFIX),
            gerritNetwork.getMetadata().getNamespace()));
  }
}
