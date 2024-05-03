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
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = GerritServiceDiscriminator.class)
public class GerritService extends GerritAbstractService {
  private static final String SERVICE_SUFFIX = "-service";

  @Override
  ServiceSpec getSpec(Gerrit gerrit) {
    return new ServiceSpecBuilder()
        .withType(gerrit.getSpec().getService().getType())
        .withPorts(getServicePorts(gerrit))
        .withSelector(GerritStatefulSet.getSelectorLabels(gerrit))
        .build();
  }

  @Override
  public String getName(String gerritName) {
    return gerritName + SERVICE_SUFFIX;
  }
}
