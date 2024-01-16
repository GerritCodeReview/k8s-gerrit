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

package com.google.gerrit.k8s.operator.receiver.dependent;

import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import java.util.Set;
import java.util.stream.Collectors;

@KubernetesDependent
public class ReceiverSecret extends KubernetesDependentResource<Secret, Receiver>
    implements SecondaryToPrimaryMapper<Secret> {
  public ReceiverSecret() {
    super(Secret.class);
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(Secret secret) {
    return client
        .resources(Receiver.class)
        .inNamespace(secret.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(g -> g.getSpec().getCredentialSecretRef().equals(secret.getMetadata().getName()))
        .map(g -> ResourceID.fromResource(g))
        .collect(Collectors.toSet());
  }
}
