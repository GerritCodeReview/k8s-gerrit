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

import com.google.gerrit.k8s.operator.v1beta3.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.v1beta3.api.model.network.GerritNetwork;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.getambassador.v2.MappingSpec;
import io.getambassador.v2.MappingSpecBuilder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import java.util.List;

public abstract class AbstractAmbassadorDependentResource<T extends HasMetadata>
    extends CRUDKubernetesDependentResource<T, GerritNetwork> {

  public AbstractAmbassadorDependentResource(Class<T> dependentResourceClass) {
    super(dependentResourceClass);
  }

  public ObjectMeta getCommonMetadata(GerritNetwork gerritnetwork, String name, String className) {
    ObjectMeta metadata =
        new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(gerritnetwork.getMetadata().getNamespace())
            .withLabels(
                GerritCluster.getLabels(gerritnetwork.getMetadata().getName(), name, className))
            .build();
    return metadata;
  }

  public MappingSpec getCommonSpec(GerritNetwork gerritnetwork, String serviceName) {
    MappingSpec spec =
        new MappingSpecBuilder()
            .withAmbassadorId(getAmbassadorIds(gerritnetwork))
            .withHost(gerritnetwork.getSpec().getIngress().getHost())
            .withPrefix("/")
            .withService(serviceName)
            .withBypassAuth(true)
            .withRewrite("") // important - so the prefix doesn't get overwritten to "/"
            .build();
    return spec;
  }

  public List<String> getAmbassadorIds(GerritNetwork gerritnetwork) {
    return gerritnetwork.getSpec().getIngress().getAmbassador().getId();
  }
}
