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

package com.google.gerrit.k8s.operator.util;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import java.util.HashMap;
import java.util.Map;

public abstract class KubernetesDependentCustomResource<
        R extends HasMetadata, P extends HasMetadata>
    extends CRUDReconcileAddKubernetesDependentResource<R, P> {
  public KubernetesDependentCustomResource(Class<R> resourceType) {
    super(resourceType);
  }

  @Override
  protected void addMetadata(
      boolean forMatch, R actualResource, final R target, P primary, Context<P> context) {
    super.addMetadata(forMatch, actualResource, target, primary, context);
    ObjectMeta metadata = target.getMetadata();
    Map<String, String> annotations = metadata.getAnnotations();
    if (annotations == null) {
      annotations = new HashMap<>();
      metadata.setAnnotations(annotations);
    }
    annotations.put("gerritoperator.google.com/apiVersion", primary.getApiVersion());
  }

  @Override
  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return GenericKubernetesResourceMatcher.match(
        desired, actualResource, true, false, true, context);
  }
}
