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

package com.google.gerrit.k8s.operator.indexer.dependent;

import static com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler.GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshot;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class GerritRepositoriesVolumeSnapshotDiscriminator
    implements ResourceDiscriminator<VolumeSnapshot, GerritIndexer> {
  @Override
  public Optional<VolumeSnapshot> distinguish(
      Class<VolumeSnapshot> aClass, GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    InformerEventSource<VolumeSnapshot, GerritIndexer> ies =
        (InformerEventSource<VolumeSnapshot, GerritIndexer>)
            context
                .eventSourceRetriever()
                .getResourceEventSourceFor(
                    VolumeSnapshot.class, GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE);

    return ies.get(
        new ResourceID(
            GerritRepositoriesVolumeSnapshot.getName(gerritIndexer),
            gerritIndexer.getMetadata().getNamespace()));
  }
}
