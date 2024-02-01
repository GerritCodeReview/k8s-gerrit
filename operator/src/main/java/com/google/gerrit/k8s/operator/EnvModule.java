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

package com.google.gerrit.k8s.operator;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.gerrit.k8s.operator.network.IngressType;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class EnvModule extends AbstractModule {

  public enum ClusterMode {
    HIGH_AVAILABILITY,
    MULTISITE
  }

  @Override
  protected void configure() {
    boolean isMultisite =
        (ClusterMode.valueOf(System.getenv("CLUSTER_MODE")).equals(ClusterMode.MULTISITE));

    if (isMultisite) {
      throw new UnsupportedOperationException("Gerrit Multisite is not yet supported.");
    }

    bind(String.class)
        .annotatedWith(Names.named("Namespace"))
        .toInstance(firstNonNull(System.getenv("NAMESPACE"), "default"));

    String ingressTypeEnv = System.getenv("INGRESS");
    IngressType ingressType =
        ingressTypeEnv == null
            ? IngressType.NONE
            : IngressType.valueOf(ingressTypeEnv.toUpperCase());
    bind(IngressType.class).annotatedWith(Names.named("IngressType")).toInstance(ingressType);
  }
}
