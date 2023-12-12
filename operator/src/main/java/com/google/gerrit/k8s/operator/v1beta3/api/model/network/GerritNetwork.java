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

package com.google.gerrit.k8s.operator.v1beta3.api.model.network;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("gerritoperator.google.com")
@Version("v1beta3")
@ShortNames("gn")
public class GerritNetwork extends CustomResource<GerritNetworkSpec, Status> implements Namespaced {
  private static final long serialVersionUID = 1L;

  public static final String SESSION_COOKIE_NAME = "Gerrit_Session";
  public static final String SESSION_COOKIE_TTL = "60s";

  @JsonIgnore
  public String getDependentResourceName(String nameSuffix) {
    return String.format("%s-%s", getMetadata().getName(), nameSuffix);
  }

  @JsonIgnore
  public boolean hasPrimaryGerrit() {
    return getSpec().getPrimaryGerrit() != null;
  }

  @JsonIgnore
  public boolean hasGerritReplica() {
    return getSpec().getGerritReplica() != null;
  }

  @JsonIgnore
  public boolean hasGerrits() {
    return hasGerritReplica() || hasPrimaryGerrit();
  }

  @JsonIgnore
  public boolean hasReceiver() {
    return getSpec().getReceiver() != null;
  }
}
