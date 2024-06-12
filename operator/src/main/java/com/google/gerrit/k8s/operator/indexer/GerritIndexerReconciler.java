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

package com.google.gerrit.k8s.operator.indexer;

import static com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler.GERRIT_INDEXER_PVC_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler.GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritIndexerConfigMap;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritIndexerJob;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritRepositoriesSnapshotPVC;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritRepositoriesVolumeSnapshot;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritRepositoriesVolumeSnapshotCondition;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritSiteSnapshotPVC;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritSiteSnapshotPVCCondition;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritSiteVolumeSnapshot;
import com.google.gerrit.k8s.operator.indexer.dependent.GerritSiteVolumeSnapshotCondition;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshot;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(
          name = "gerrit-indexer-site-snapshot",
          type = GerritSiteVolumeSnapshot.class,
          reconcilePrecondition = GerritSiteVolumeSnapshotCondition.class,
          useEventSourceWithName = GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-indexer-site-snapshot-pvc",
          type = GerritSiteSnapshotPVC.class,
          reconcilePrecondition = GerritSiteSnapshotPVCCondition.class,
          dependsOn = "gerrit-indexer-site-snapshot",
          useEventSourceWithName = GERRIT_INDEXER_PVC_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-indexer-repositories-snapshot",
          type = GerritRepositoriesVolumeSnapshot.class,
          reconcilePrecondition = GerritRepositoriesVolumeSnapshotCondition.class,
          useEventSourceWithName = GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE),
      @Dependent(
          name = "gerrit-indexer-repositories-snapshot-pvc",
          type = GerritRepositoriesSnapshotPVC.class,
          reconcilePrecondition = GerritRepositoriesVolumeSnapshotCondition.class,
          dependsOn = "gerrit-indexer-repositories-snapshot",
          useEventSourceWithName = GERRIT_INDEXER_PVC_EVENT_SOURCE),
      @Dependent(name = "gerrit-indexer-configmap", type = GerritIndexerConfigMap.class),
      @Dependent(
          name = "gerrit-indexer-job",
          type = GerritIndexerJob.class,
          dependsOn = "gerrit-indexer-configmap")
    })
public class GerritIndexerReconciler
    implements Reconciler<GerritIndexer>, EventSourceInitializer<GerritIndexer> {
  public static final String GERRIT_INDEXER_PVC_EVENT_SOURCE = "gerrit-indexer-pvc-event-source";
  public static final String GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE =
      "gerrit-indexer-volume-snapshot-event-source";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritIndexer> context) {
    Map<String, EventSource> eventSources = new HashMap<>();

    InformerEventSource<PersistentVolumeClaim, GerritIndexer> pvcEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(PersistentVolumeClaim.class, context).build(), context);
    eventSources.put(GERRIT_INDEXER_PVC_EVENT_SOURCE, pvcEventSource);

    InformerEventSource<VolumeSnapshot, GerritIndexer> volumeSnapshotEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(VolumeSnapshot.class, context).build(), context);
    eventSources.put(GERRIT_INDEXER_VOLUME_SNAPSHOT_EVENT_SOURCE, volumeSnapshotEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritIndexer> reconcile(
      GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    return UpdateControl.noUpdate();
  }
}
