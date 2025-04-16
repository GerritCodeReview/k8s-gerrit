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

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexerSpec;
import com.google.gerrit.k8s.operator.api.model.shared.IndexType;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.components.GerritSecurityContext;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritInitConfigMap;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GerritIndexerJob
    extends CRUDReconcileAddKubernetesDependentResource<Job, GerritIndexer> {
  private static final Quantity HOME_DIR_SIZE_LIMIT = new Quantity("500", "Mi");
  private static final String TMP_VOLUME_NAME = "tmp";

  public GerritIndexerJob() {
    super(Job.class);
  }

  @Override
  protected Job desired(GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    String ns = gerritIndexer.getMetadata().getNamespace();
    KubernetesClient client = context.getClient();
    GerritCluster gerritCluster =
        client
            .resources(GerritCluster.class)
            .inNamespace(ns)
            .withName(gerritIndexer.getSpec().getCluster())
            .get();

    Job existingJob =
        client.batch().v1().jobs().inNamespace(ns).withName(getName(gerritIndexer)).get();

    GerritIndexerSpec indexerSpec = gerritIndexer.getSpec();
    if (indexerSpec.getIndex() == null) {
      indexerSpec.setIndex(gerritCluster.getSpec().getIndex());
    }

    Job desired =
        new JobBuilder()
            .withApiVersion("batch/v1")
            .withNewMetadata()
            .withName(getName(gerritIndexer))
            .withNamespace(ns)
            .withLabels(getLabels(gerritIndexer))
            .endMetadata()
            .withNewSpec()
            .withManualSelector(true)
            .withSelector(
                new LabelSelectorBuilder().withMatchLabels(getLabels(gerritIndexer)).build())
            .withNewTemplate()
            .withNewMetadata()
            .withAnnotations(
                Map.of(
                    "cluster-autoscaler.kubernetes.io/safe-to-evict", "false",
                    "sidecar.istio.io/inject", "false"))
            .withLabels(getLabels(gerritIndexer))
            .endMetadata()
            .withNewSpec()
            .withAffinity(indexerSpec.getAffinity())
            .withTolerations(indexerSpec.getTolerations())
            .addAllToImagePullSecrets(
                gerritCluster.getSpec().getContainerImages().getImagePullSecrets())
            .withRestartPolicy("OnFailure")
            .withSecurityContext(GerritSecurityContext.forPod())
            .withInitContainers(buildGerritInitContainer(indexerSpec, gerritCluster))
            .withContainers(buildGerritIndexerContainer(indexerSpec, gerritCluster))
            .withVolumes(buildVolumes(gerritIndexer, gerritCluster))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    if (existingJob != null && !desired.getSpec().equals(existingJob.getSpec())) {
      client.resource(existingJob).delete();
    }

    return desired;
  }

  public static String getName(GerritIndexer gerritIndexer) {
    return getName(gerritIndexer.getMetadata().getName());
  }

  public static String getName(String gerritIndexerName) {
    return gerritIndexerName;
  }

  private static Map<String, String> getLabels(GerritIndexer gerritIndexer) {
    String name = gerritIndexer.getMetadata().getName();
    return GerritClusterLabelFactory.create(
        name, getComponentName(name), GerritIndexerReconciler.class.getSimpleName());
  }

  private static String getComponentName(String gerritIndexerName) {
    return String.format("gerrit-indexer-%s", gerritIndexerName);
  }

  private Container buildGerritInitContainer(
      GerritIndexerSpec indexerSpec, GerritCluster gerritCluster) {
    return new ContainerBuilder()
        .withName("gerrit-init")
        .withSecurityContext(GerritSecurityContext.forContainer())
        .withImage(
            gerritCluster
                .getSpec()
                .getContainerImages()
                .getGerritImages()
                .getFullImageName("gerrit-init"))
        .withImagePullPolicy(gerritCluster.getSpec().getContainerImages().getImagePullPolicy())
        .withResources(indexerSpec.getResources())
        .withVolumeMounts(buildGerritInitVolumeMounts(indexerSpec))
        .build();
  }

  private Container buildGerritIndexerContainer(
      GerritIndexerSpec indexerSpec, GerritCluster gerritCluster) {
    ContainerBuilder builder =
        new ContainerBuilder()
            .withName("gerrit-indexer")
            .withSecurityContext(GerritSecurityContext.forContainer())
            .withImage(
                gerritCluster
                    .getSpec()
                    .getContainerImages()
                    .getGerritImages()
                    .getFullImageName("gerrit-indexer"))
            .withImagePullPolicy(gerritCluster.getSpec().getContainerImages().getImagePullPolicy())
            .withResources(indexerSpec.getResources())
            .withVolumeMounts(buildGerritIndexerVolumeMounts(indexerSpec));

    if (indexerSpec.getIndex().getType() == IndexType.LUCENE) {
      builder.withArgs("--output", "/indexes");
    }

    return builder.build();
  }

  private List<VolumeMount> buildGerritInitVolumeMounts(GerritIndexerSpec indexerSpec) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-init-config")
            .withMountPath("/var/config")
            .build());

    volumeMounts.addAll(buildCommonVolumeMounts(indexerSpec));

    return volumeMounts;
  }

  private List<VolumeMount> buildGerritIndexerVolumeMounts(GerritIndexerSpec indexerSpec) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    if (indexerSpec.getIndex().getType() == IndexType.LUCENE) {
      volumeMounts.add(
          new VolumeMountBuilder()
              .withName(outputVolumeName(indexerSpec))
              .withSubPath(indexerSpec.getStorage().getOutput().getSubPath())
              .withMountPath("/indexes")
              .build());
    }

    volumeMounts.addAll(buildCommonVolumeMounts(indexerSpec));

    return volumeMounts;
  }

  private List<VolumeMount> buildCommonVolumeMounts(GerritIndexerSpec indexerSpec) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(TMP_VOLUME_NAME)
            .withMountPath("/home/gerrit")
            .withSubPath("home")
            .build());
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(TMP_VOLUME_NAME)
            .withMountPath("/tmp")
            .withSubPath("tmp")
            .build());

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-config")
            .withMountPath("/var/mnt/etc/config")
            .build());

    String siteSubPath = indexerSpec.getStorage().getSite().getSubPath();
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-site")
            .withMountPath("/var/gerrit")
            .withSubPath(siteSubPath)
            .build());

    String repoPath = indexerSpec.getStorage().getRepositories().getSubPath();
    VolumeMountBuilder repoVolumeMount =
        new VolumeMountBuilder()
            .withName(repoVolumeName(indexerSpec))
            .withMountPath("/var/mnt/git")
            .withSubPath(repoPath);
    if (repoPath == null) {
      repoVolumeMount.withSubPath("git");
    } else {
      repoVolumeMount.withSubPath(repoPath);
    }
    volumeMounts.add(repoVolumeMount.build());
    return volumeMounts;
  }

  private List<Volume> buildVolumes(GerritIndexer gerritIndexer, GerritCluster gerritCluster) {
    GerritIndexerSpec indexerSpec = gerritIndexer.getSpec();
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-site")
            .withNewPersistentVolumeClaim()
            .withClaimName(indexerSpec.getStorage().getSite().getPersistentVolumeClaim())
            .endPersistentVolumeClaim()
            .build());

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-config")
            .withNewConfigMap()
            .withName(GerritIndexerConfigMap.getName(gerritIndexer))
            .endConfigMap()
            .build());

    // TODO: Check whether primary exists on creation
    GerritTemplate primaryGerrit =
        gerritCluster.getSpec().getGerrits().stream()
            .filter(g -> g.getSpec().getMode().equals(GerritMode.PRIMARY))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No primary Gerrit is part of the GerritCluster."));

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-init-config")
            .withNewConfigMap()
            .withName(GerritInitConfigMap.getName(primaryGerrit.getMetadata().getName()))
            .endConfigMap()
            .build());

    if (indexerSpec.getIndex().getType() == IndexType.LUCENE) {
      if (isSeparateOutputVolume(indexerSpec)) {
        volumes.add(
            new VolumeBuilder()
                .withName(repoVolumeName(indexerSpec))
                .withNewPersistentVolumeClaim()
                .withClaimName(
                    indexerSpec.getStorage().getRepositories().getPersistentVolumeClaim())
                .endPersistentVolumeClaim()
                .build());
        volumes.add(
            new VolumeBuilder()
                .withName(outputVolumeName(indexerSpec))
                .withNewPersistentVolumeClaim()
                .withClaimName(indexerSpec.getStorage().getOutput().getPersistentVolumeClaim())
                .endPersistentVolumeClaim()
                .build());
      } else {
        volumes.add(
            new VolumeBuilder()
                .withName(outputVolumeName(indexerSpec))
                .withNewPersistentVolumeClaim()
                .withClaimName(
                    indexerSpec.getStorage().getRepositories().getPersistentVolumeClaim())
                .endPersistentVolumeClaim()
                .build());
      }
    } else {
      volumes.add(
          new VolumeBuilder()
              .withName(repoVolumeName(indexerSpec))
              .withNewPersistentVolumeClaim()
              .withClaimName(indexerSpec.getStorage().getRepositories().getPersistentVolumeClaim())
              .endPersistentVolumeClaim()
              .build());
    }

    volumes.add(
        new VolumeBuilder()
            .withEmptyDir(
                new EmptyDirVolumeSourceBuilder().withSizeLimit(HOME_DIR_SIZE_LIMIT).build())
            .withName(TMP_VOLUME_NAME)
            .build());

    return volumes;
  }

  private String outputVolumeName(GerritIndexerSpec indexerSpec) {
    String outputVolumeName = "index-output";

    if (!isSeparateOutputVolume(indexerSpec)) {
      outputVolumeName = "shared";
    }
    return outputVolumeName;
  }

  private String repoVolumeName(GerritIndexerSpec indexerSpec) {
    String repoVolumeName = "repositories";

    if (!isSeparateOutputVolume(indexerSpec)) {
      repoVolumeName = "shared";
    }
    return repoVolumeName;
  }

  private boolean isSeparateOutputVolume(GerritIndexerSpec indexerSpec) {
    String outputPvc = indexerSpec.getStorage().getOutput().getPersistentVolumeClaim();

    return !outputPvc.equals(indexerSpec.getStorage().getRepositories().getPersistentVolumeClaim());
  }
}
