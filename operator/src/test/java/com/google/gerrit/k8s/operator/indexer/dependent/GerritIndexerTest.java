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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshot;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshotClass;
import io.fabric8.volumesnapshot.api.model.VolumeSnapshotClassBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GenericRetryExecution;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritIndexerTest {
  private static final String GET_RWO_STORAGE_CLASS_PATH =
      "/apis/storage.k8s.io/v1/storageclasses/default";
  private static final String GET_RWM_STORAGE_CLASS_PATH =
      "/apis/storage.k8s.io/v1/storageclasses/shared-storage";
  private static final String GET_SITE_PVC_PATH =
      "/api/v1/namespaces/gerrit/persistentvolumeclaims/gerrit-site-gerrit-0";
  private static final String GET_SHARED_PVC_PATH =
      "/api/v1/namespaces/gerrit/persistentvolumeclaims/shared-pvc";
  private static final String LIST_SNAPSHOT_CLASSES_PATH =
      "/apis/snapshot.storage.k8s.io/v1/volumesnapshotclasses";
  private static final String GET_GERRIT_CLUSTER_PATH =
      String.format(
          "/apis/%s/namespaces/gerrit/%s/gerrit",
          HasMetadata.getApiVersion(GerritCluster.class),
          HasMetadata.getPlural(GerritCluster.class));
  private static final String GET_GERRIT_PATH =
      String.format(
          "/apis/%s/namespaces/gerrit/%s/gerrit",
          HasMetadata.getApiVersion(Gerrit.class), HasMetadata.getPlural(Gerrit.class));
  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    kubernetesServer.before();
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifestsWithSnapshot")
  public void expectedGerritIndexerWithSnapshotCreated(
      String inputFile,
      String gerritClusterFile,
      String expectedJob,
      String expectedSiteVolumeSnapshot,
      String expectedRepoVolumeSnapshot,
      String expectedSitePVC,
      String expectedRepoPVC,
      String expectedConfigmap) {
    GerritIndexer input = ReconcilerUtils.loadYaml(GerritIndexer.class, this.getClass(), inputFile);

    GerritCluster gerritCluster =
        ReconcilerUtils.loadYaml(GerritCluster.class, this.getClass(), gerritClusterFile);

    stubs(gerritCluster);

    Context<GerritIndexer> context = getContext(new GerritIndexerReconciler(), input);
    GerritIndexerJob dependentCronjob = new GerritIndexerJob();
    assertThat(dependentCronjob.desired(input, context))
        .isEqualTo(ReconcilerUtils.loadYaml(Job.class, this.getClass(), expectedJob));

    GerritSiteVolumeSnapshot dependentSiteSnapshot = new GerritSiteVolumeSnapshot();
    assertThat(dependentSiteSnapshot.desired(input, context))
        .isEqualTo(
            ReconcilerUtils.loadYaml(
                VolumeSnapshot.class, this.getClass(), expectedSiteVolumeSnapshot));

    GerritRepositoriesVolumeSnapshot dependentRepoSnapshot = new GerritRepositoriesVolumeSnapshot();
    assertThat(dependentRepoSnapshot.desired(input, context))
        .isEqualTo(
            ReconcilerUtils.loadYaml(
                VolumeSnapshot.class, this.getClass(), expectedRepoVolumeSnapshot));

    GerritSiteSnapshotPVC dependentSitePVC = new GerritSiteSnapshotPVC();
    assertThat(dependentSitePVC.desired(input, context))
        .isEqualTo(
            ReconcilerUtils.loadYaml(
                PersistentVolumeClaim.class, this.getClass(), expectedSitePVC));

    GerritRepositoriesSnapshotPVC dependentRepoPVC = new GerritRepositoriesSnapshotPVC();
    assertThat(dependentRepoPVC.desired(input, context))
        .isEqualTo(
            ReconcilerUtils.loadYaml(
                PersistentVolumeClaim.class, this.getClass(), expectedRepoPVC));

    GerritIndexerConfigMap dependentConfigmap = new GerritIndexerConfigMap();
    assertDesiredConfigMapCreated(
        dependentConfigmap.desired(input, context),
        ReconcilerUtils.loadYaml(ConfigMap.class, this.getClass(), expectedConfigmap));
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifestsWithoutSnapshot")
  public void expectedGerritIndexerWithoutSnapshotCreated(
      String inputFile, String gerritClusterFile, String expectedJob, String expectedConfigmap) {
    GerritIndexer input = ReconcilerUtils.loadYaml(GerritIndexer.class, this.getClass(), inputFile);

    GerritCluster gerritCluster =
        ReconcilerUtils.loadYaml(GerritCluster.class, this.getClass(), gerritClusterFile);

    stubs(gerritCluster);

    Context<GerritIndexer> context = getContext(new GerritIndexerReconciler(), input);
    GerritIndexerJob dependentCronjob = new GerritIndexerJob();
    assertThat(dependentCronjob.desired(input, context))
        .isEqualTo(ReconcilerUtils.loadYaml(Job.class, this.getClass(), expectedJob));

    GerritIndexerConfigMap dependentConfigmap = new GerritIndexerConfigMap();
    assertDesiredConfigMapCreated(
        dependentConfigmap.desired(input, context),
        ReconcilerUtils.loadYaml(ConfigMap.class, this.getClass(), expectedConfigmap));
  }

  private void stubs(GerritCluster gerritCluster) {
    kubernetesServer
        .expect()
        .get()
        .withPath(GET_GERRIT_CLUSTER_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, gerritCluster)
        .always();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_GERRIT_PATH)
        .andReturn(
            HttpURLConnection.HTTP_OK,
            gerritCluster.getSpec().getGerrits().get(0).toGerrit(gerritCluster))
        .always();

    String rwoProvisioner = "pd.csi.storage.gke.io";
    StorageClass defaultStorageClass =
        new StorageClassBuilder()
            .withNewMetadata()
            .withName("default")
            .endMetadata()
            .withProvisioner(rwoProvisioner)
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_RWO_STORAGE_CLASS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, defaultStorageClass)
        .once();

    String rwmProvisioner = "nfs";
    StorageClass rwmStorageClass =
        new StorageClassBuilder()
            .withNewMetadata()
            .withName("default")
            .endMetadata()
            .withProvisioner(rwmProvisioner)
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_RWM_STORAGE_CLASS_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, rwmStorageClass)
        .once();

    DefaultKubernetesResourceList<VolumeSnapshotClass> snapshotClasses =
        new DefaultKubernetesResourceList<>();
    snapshotClasses.setItems(
        List.of(
            new VolumeSnapshotClassBuilder()
                .withNewMetadata()
                .withName("default")
                .endMetadata()
                .withDriver(rwoProvisioner)
                .build(),
            new VolumeSnapshotClassBuilder()
                .withNewMetadata()
                .withName("shared-storage")
                .endMetadata()
                .withDriver(rwmProvisioner)
                .build()));

    kubernetesServer
        .expect()
        .get()
        .withPath(LIST_SNAPSHOT_CLASSES_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, snapshotClasses)
        .always();

    PersistentVolumeClaim siteVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("gerrit-site-gerrit-0")
            .endMetadata()
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_SITE_PVC_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, siteVolumeClaim)
        .once();

    PersistentVolumeClaim sharedVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("shared-pvc")
            .endMetadata()
            .build();

    kubernetesServer
        .expect()
        .get()
        .withPath(GET_SHARED_PVC_PATH)
        .andReturn(HttpURLConnection.HTTP_OK, sharedVolumeClaim)
        .once();
  }

  private Context<GerritIndexer> getContext(
      Reconciler<GerritIndexer> reconciler, GerritIndexer primary) {
    Controller<GerritIndexer> controller =
        new Controller<GerritIndexer>(
            reconciler,
            new BaseConfigurationService().getConfigurationFor(reconciler),
            kubernetesServer.getClient());

    return new DefaultContext<GerritIndexer>(
        new GenericRetryExecution(new GenericRetry()), controller, primary);
  }

  private void assertDesiredConfigMapCreated(ConfigMap actual, ConfigMap expected) {
    assertThat(actual.getMetadata()).isEqualTo(expected.getMetadata());
    assertThat(actual.getData().keySet()).isEqualTo(expected.getData().keySet());
    for (Map.Entry<String, String> file : actual.getData().entrySet()) {
      assertThat(file.getValue().replaceAll("\\s+", "").trim())
          .isEqualTo(expected.getData().get(file.getKey()).replaceAll("\\s+", "").trim());
    }
  }

  private static Stream<Arguments> provideYamlManifestsWithSnapshot() {
    return Stream.of(
        Arguments.of(
            "../indexer.yaml",
            "../gerritcluster_minimal.yaml",
            "indexer.job.yaml",
            "indexer.site.snapshot.yaml",
            "indexer.repo.snapshot.yaml",
            "indexer.site.pvc.yaml",
            "indexer.repo.pvc.yaml",
            "indexer.configmap.yaml"));
  }

  private static Stream<Arguments> provideYamlManifestsWithoutSnapshot() {
    return Stream.of(
        Arguments.of(
            "../indexer_without_snapshot.yaml",
            "../gerritcluster_minimal.yaml",
            "indexer.job.without-snapshot.yaml",
            "indexer.configmap.yaml"));
  }
}
