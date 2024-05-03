// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritServiceDiscriminator.class)
public class GerritService extends GerritAbstractService {

  private static final String SERVICE_SUFFIX = "-service";

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    return new ServiceBuilder()
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withType(gerrit.getSpec().getService().getType())
        .withPorts(getServicePorts(gerrit))
        .withSelector(GerritStatefulSet.getSelectorLabels(gerrit))
        .endSpec()
        .build();
  }

  @Override
  public String getComponentName(Gerrit gerrit) {
    return gerrit.getMetadata().getName() + SERVICE_SUFFIX;
  }
}
