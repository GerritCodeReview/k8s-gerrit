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
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericResourceUpdatePreProcessor;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KubernetesDependentCustomResource<
        R extends HasMetadata, P extends HasMetadata>
    extends CRUDReconcileAddKubernetesDependentResource<R, P> {

  private static final Logger log =
      LoggerFactory.getLogger(KubernetesDependentCustomResource.class);

  public KubernetesDependentCustomResource(Class<R> resourceType) {
    super(resourceType);
  }

  protected R setApiVersionAnnotation(R updated, P primary) {
    ObjectMeta meta = updated.getMetadata();
    Map<String, String> actualAnnotations = meta.getAnnotations();
    actualAnnotations.put("gerritoperator.google.com/apiVersion", primary.getApiVersion());
    meta.setAnnotations(actualAnnotations);
    updated.setMetadata(meta);
    return updated;
  }

  @Override
  public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    return GenericKubernetesResourceMatcher.match(desired, actualResource, true, true);
  }

  @Override
  public R create(R target, P primary, Context<P> context) {
    return prepare(setApiVersionAnnotation(target, primary), primary, "Creating").create();
  }

  @Override
  public R update(R actual, R target, P primary, Context<P> context) {
    R updatedActual =
        GenericResourceUpdatePreProcessor.processorFor(resourceType())
            .replaceSpecOnActual(actual, target, context);
    updatedActual = setApiVersionAnnotation(updatedActual, primary);
    return prepare(updatedActual, primary, "Updating").replace();
  }

  protected Resource<R> prepare(R desired, P primary, String actionName) {
    log.debug(
        "{} target resource with type: {}, with id: {}",
        actionName,
        desired.getClass(),
        ResourceID.fromResource(desired));

    desired.addOwnerReference(primary);

    if (desired instanceof Namespaced) {
      return client.resource(desired).inNamespace(desired.getMetadata().getNamespace());
    } else {
      return client.resource(desired);
    }
  }
}
