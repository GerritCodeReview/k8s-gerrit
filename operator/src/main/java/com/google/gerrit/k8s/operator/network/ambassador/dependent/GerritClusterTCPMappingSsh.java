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

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import io.getambassador.v2.TCPMapping;
import io.getambassador.v2.TCPMappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritClusterTCPMappingSshDiscriminator.class)
public class GerritClusterTCPMappingSsh extends AbstractAmbassadorDependentResource<TCPMapping> {

  public static final String GERRIT_TCP_MAPPING_SSH = "gerrit-tcp-mapping-ssh";

  public GerritClusterTCPMappingSsh() {
    super(TCPMapping.class);
  }

  // Assumes hasPrimaryGerrit() — guarded by SshTcpMappingCondition reconcile precondition
  @Override
  protected TCPMapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String namespace = gerritNetwork.getMetadata().getNamespace();
    int sshPort = gerritNetwork.getSpec().getPrimaryGerrit().getSshPort();
    String sshServiceName =
        new GerritService()
                .getHostname(gerritNetwork.getSpec().getPrimaryGerrit().getName(), namespace)
            + ":"
            + sshPort;

    TCPMapping tcpMapping =
        new TCPMappingBuilder()
            .withNewMetadataLike(
                getCommonMetadata(
                    gerritNetwork, GERRIT_TCP_MAPPING_SSH, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpec()
            .withAmbassadorId(getAmbassadorIds(gerritNetwork))
            .withPort(Long.valueOf(sshPort))
            .withService(sshServiceName)
            .endSpec()
            .build();

    return tcpMapping;
  }
}
