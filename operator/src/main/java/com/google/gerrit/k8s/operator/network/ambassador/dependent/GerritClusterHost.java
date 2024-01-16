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

import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterTLSContext.GERRIT_TLS_CONTEXT;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import io.getambassador.v2.Host;
import io.getambassador.v2.HostBuilder;
import io.getambassador.v2.hostspec.TlsContext;
import io.getambassador.v2.hostspec.TlsSecret;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class GerritClusterHost extends AbstractAmbassadorDependentResource<Host> {

  public static final String GERRIT_HOST = "gerrit-ambassador-host";

  public GerritClusterHost() {
    super(Host.class);
  }

  @Override
  public Host desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    TlsSecret tlsSecret = null;
    TlsContext tlsContext = null;

    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      tlsSecret = new TlsSecret();
      tlsContext = new TlsContext();
      tlsSecret.setName(gerritNetwork.getSpec().getIngress().getTls().getSecret());
      tlsContext.setName(GERRIT_TLS_CONTEXT);
    }

    Host host =
        new HostBuilder()
            .withNewMetadataLike(
                getCommonMetadata(gerritNetwork, GERRIT_HOST, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpec()
            .withAmbassadorId(getAmbassadorIds(gerritNetwork))
            .withHostname(gerritNetwork.getSpec().getIngress().getHost())
            .withTlsSecret(tlsSecret)
            .withTlsContext(tlsContext)
            .endSpec()
            .build();

    return host;
  }
}
