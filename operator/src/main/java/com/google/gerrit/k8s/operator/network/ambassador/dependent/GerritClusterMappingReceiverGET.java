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

import static com.google.gerrit.k8s.operator.network.Constants.INFO_REFS_PATTERN;

import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.v1beta1.api.model.network.GerritNetwork;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.MappingBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;

@KubernetesDependent(resourceDiscriminator = GerritClusterMappingReceiverGETDiscriminator.class)
public class GerritClusterMappingReceiverGET extends AbstractAmbassadorDependentResource<Mapping>
    implements MappingDependentResourceInterface {

  public static final String GERRIT_MAPPING_RECEIVER_GET = "gerrit-mapping-receiver-get";

  public GerritClusterMappingReceiverGET() {
    super(Mapping.class);
  }

  @Override
  public Mapping desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    String receiverServiceName =
        ReceiverService.getName(gerritNetwork.getSpec().getReceiver().getName())
            + ":"
            + gerritNetwork.getSpec().getReceiver().getHttpPort();

    Mapping mapping =
        new MappingBuilder()
            .withNewMetadataLike(
                getCommonMetadata(
                    gerritNetwork, GERRIT_MAPPING_RECEIVER_GET, this.getClass().getSimpleName()))
            .endMetadata()
            .withNewSpecLike(getCommonSpec(gerritNetwork, receiverServiceName))
            .withNewV2QueryParameters()
            .withAdditionalProperties(
                new HashMap<String, Object>() {
                  {
                    put("service", "git-receive-pack");
                  }
                })
            .endV2QueryParameters()
            .withMethod("GET")
            .withPrefix(INFO_REFS_PATTERN)
            .withPrefixRegex(true)
            .endSpec()
            .build();
    return mapping;
  }
}
