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

package com.google.gerrit.k8s.operator.api.model.gerrit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gerrit.k8s.operator.Constants;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Group("gerritoperator.google.com")
@Version(Constants.VERSION)
@ShortNames("gcr")
public class Gerrit extends CustomResource<GerritSpec, GerritStatus> implements Namespaced {
  private static final long serialVersionUID = 2L;

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

  @JsonIgnore
  public boolean isSshEnabled() {
    return getSpec().getService().getSshPort() > 0;
  }

  @JsonIgnore
  public Set<String> getModuleDataSecretNames() {
    return getSpec().getAllGerritModules().stream()
        .filter(m -> m.getModuleData() != null)
        .map(GerritModule::getModuleData)
        .map(GerritModuleData::getSecretRef)
        .filter(s -> s != null)
        .collect(Collectors.toSet());
  }
}
