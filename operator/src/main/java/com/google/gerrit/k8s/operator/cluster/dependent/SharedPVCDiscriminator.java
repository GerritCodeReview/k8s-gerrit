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

package com.google.gerrit.k8s.operator.cluster.dependent;

import static com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler.PVC_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.v1beta4.api.model.cluster.GerritCluster;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class SharedPVCDiscriminator
    implements ResourceDiscriminator<PersistentVolumeClaim, GerritCluster> {
  @Override
  public Optional<PersistentVolumeClaim> distinguish(
      Class<PersistentVolumeClaim> resource,
      GerritCluster primary,
      Context<GerritCluster> context) {
    InformerEventSource<PersistentVolumeClaim, GerritCluster> ies =
        (InformerEventSource<PersistentVolumeClaim, GerritCluster>)
            context
                .eventSourceRetriever()
                .getResourceEventSourceFor(PersistentVolumeClaim.class, PVC_EVENT_SOURCE);

    return ies.get(new ResourceID(SharedPVC.SHARED_PVC_NAME, primary.getMetadata().getNamespace()));
  }
}
