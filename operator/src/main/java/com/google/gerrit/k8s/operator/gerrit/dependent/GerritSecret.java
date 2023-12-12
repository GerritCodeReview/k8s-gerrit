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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import com.google.gerrit.k8s.operator.v1beta3.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import java.util.Set;
import java.util.stream.Collectors;

@KubernetesDependent(resourceDiscriminator = GerritSecretDiscriminator.class)
public class GerritSecret extends KubernetesDependentResource<Secret, Gerrit>
    implements SecondaryToPrimaryMapper<Secret> {

  public static final String CONTEXT_SECRET_VERSION_KEY = "gerrit-secret-version";

  public GerritSecret() {
    super(Secret.class);
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(Secret secret) {
    return client
        .resources(Gerrit.class)
        .inNamespace(secret.getMetadata().getNamespace())
        .list()
        .getItems()
        .stream()
        .filter(g -> g.getSpec().getSecretRef().equals(secret.getMetadata().getName()))
        .map(g -> ResourceID.fromResource(g))
        .collect(Collectors.toSet());
  }

  @Override
  protected ReconcileResult<Secret> reconcile(
      Gerrit primary, Secret actualResource, Context<Gerrit> context) {
    Secret sec =
        client
            .secrets()
            .inNamespace(primary.getMetadata().getNamespace())
            .withName(primary.getSpec().getSecretRef())
            .get();
    if (sec != null) {
      context
          .managedDependentResourceContext()
          .put(CONTEXT_SECRET_VERSION_KEY, sec.getMetadata().getResourceVersion());
    }
    return ReconcileResult.noOperation(actualResource);
  }
}
