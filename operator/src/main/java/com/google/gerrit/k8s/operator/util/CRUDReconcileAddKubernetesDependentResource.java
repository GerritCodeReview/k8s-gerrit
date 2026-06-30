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

package com.google.gerrit.k8s.operator.util;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;

public abstract class CRUDReconcileAddKubernetesDependentResource<
        R extends HasMetadata, P extends HasMetadata>
    extends CRUDKubernetesDependentResource<R, P> {

  public CRUDReconcileAddKubernetesDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  @Override
  public Result<R> match(R actualResource, P primary, Context<P> context) {
    return match(actualResource, desired(primary, context), primary, context);
  }

  @Override
  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return GenericKubernetesResourceMatcher.match(desired, actualResource, false, true, context);
  }
}
