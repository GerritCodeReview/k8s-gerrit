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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritHeadlessServiceDiscriminator.class)
public class GerritHeadlessService extends GerritAbstractService {
  private static final String HEADLESS_SERVICE_SUFFIX = "-headless";

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    return new ServiceBuilder()
        .withNewMetadata()
        .withName(getName(gerrit))
        .withNamespace(gerrit.getMetadata().getNamespace())
        .withLabels(getLabels(gerrit))
        .endMetadata()
        .withNewSpec()
        .withClusterIP("None")
        .withPorts(GerritServiceUtil.getServicePorts(gerrit))
        .withSelector(GerritStatefulSet.getSelectorLabels(gerrit))
        .endSpec()
        .build();
  }

  public static String getName(Gerrit gerrit) {
    return gerrit.getMetadata().getName() + HEADLESS_SERVICE_SUFFIX;
  }

  @Override
  public String getComponentName(Gerrit gerrit) {
    return getName(gerrit);
  }
}
