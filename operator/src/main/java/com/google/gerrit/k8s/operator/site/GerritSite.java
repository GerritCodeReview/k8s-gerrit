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

package com.google.gerrit.k8s.operator.site;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Group("gerritoperator.google.com")
@Version("v1alpha1")
@ShortNames("gs")
public class GerritSite extends CustomResource<GerritSiteSpec, GerritSiteStatus>
    implements Namespaced {
  private static final long serialVersionUID = 1L;

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

  public Map<String, String> getLabels(String component, String createdBy) {
    Map<String, String> labels = new HashMap<>();

    labels.put("app.kubernetes.io/name", "gerrit");
    labels.put("app.kubernetes.io/instance", getMetadata().getName());
    labels.put("app.kubernetes.io/version", getClass().getPackage().getImplementationVersion());
    labels.put("app.kubernetes.io/component", component);
    labels.put("app.kubernetes.io/part-of", getMetadata().getName());
    labels.put("app.kubernetes.io/managed-by", "gerrit-operator");
    labels.put("app.kubernetes.io/created-by", createdBy);

    return labels;
  }

  @Override
  protected GerritSiteStatus initStatus() {
    return new GerritSiteStatus();
  }
}
