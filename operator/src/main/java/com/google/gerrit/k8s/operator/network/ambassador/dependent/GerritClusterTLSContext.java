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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import com.google.gerrit.k8s.operator.v1beta2.api.model.network.GerritNetwork;
import io.getambassador.v2.TLSContext;
import io.getambassador.v2.TLSContextBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.List;

public class GerritClusterTLSContext extends AbstractAmbassadorDependentResource<TLSContext> {

  public static final String GERRIT_TLS_CONTEXT = "gerrit-tls-context";

  public GerritClusterTLSContext() {
    super(TLSContext.class);
  }

  @Override
  protected TLSContext desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    TLSContext tlsContext =
        new TLSContextBuilder()
            .withNewMetadataLike(
                getCommonMetadata(
                    gerritNetwork, GERRIT_TLS_CONTEXT, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpec()
            .withAmbassadorId(getAmbassadorIds(gerritNetwork))
            .withSecret(gerritNetwork.getSpec().getIngress().getTls().getSecret())
            .withHosts(List.of(gerritNetwork.getSpec().getIngress().getHost()))
            .withSecretNamespacing(true)
            .endSpec()
            .build();
    return tlsContext;
  }
}
