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

import static com.google.gerrit.k8s.operator.Constants.GERRIT_USER_GROUP_ID;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplate;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritInitConfigMap;
import com.google.gerrit.k8s.operator.indexer.GerritIndexerReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GerritIndexerJob
    extends CRUDReconcileAddKubernetesDependentResource<Job, GerritIndexer> {

  public GerritIndexerJob() {
    super(Job.class);
  }

  @Override
  protected Job desired(GerritIndexer gerritIndexer, Context<GerritIndexer> context) {
    String ns = gerritIndexer.getMetadata().getNamespace();
    GerritCluster gerritCluster =
        context
            .getClient()
            .resources(GerritCluster.class)
            .inNamespace(ns)
            .withName(gerritIndexer.getSpec().getCluster())
            .get();

    return new JobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withName(getName(gerritIndexer))
        .withNamespace(ns)
        .withLabels(getLabels(gerritIndexer))
        .endMetadata()
        .withNewSpec()
        .withManualSelector(true)
        .withSelector(new LabelSelectorBuilder().withMatchLabels(getLabels(gerritIndexer)).build())
        .withNewTemplate()
        .withNewMetadata()
        .withAnnotations(
            Map.of(
                "cluster-autoscaler.kubernetes.io/safe-to-evict", "false",
                "sidecar.istio.io/inject", "false"))
        .withLabels(getLabels(gerritIndexer))
        .endMetadata()
        .withNewSpec()
        .addAllToImagePullSecrets(
            gerritCluster.getSpec().getContainerImages().getImagePullSecrets())
        .withRestartPolicy("OnFailure")
        .withNewSecurityContext()
        .withFsGroup(GERRIT_USER_GROUP_ID)
        .endSecurityContext()
        .withInitContainers(buildGerritInitContainer(gerritIndexer, gerritCluster))
        .withContainers(buildGerritIndexerContainer(gerritIndexer, gerritCluster))
        .withVolumes(buildVolumes(gerritIndexer, gerritCluster))
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  public static String getName(GerritIndexer gerritIndexer) {
    return getName(gerritIndexer.getMetadata().getName());
  }

  public static String getName(String gerritIndexerName) {
    return gerritIndexerName;
  }

  private static Map<String, String> getLabels(GerritIndexer gerritIndexer) {
    String name = gerritIndexer.getMetadata().getName();
    return GerritCluster.getLabels(
        name, getComponentName(name), GerritIndexerReconciler.class.getSimpleName());
  }

  private static String getComponentName(String gerritIndexerName) {
    return String.format("gerrit-indexer-%s", gerritIndexerName);
  }

  private Container buildGerritInitContainer(
      GerritIndexer gerritIndexer, GerritCluster gerritCluster) {
    return new ContainerBuilder()
        .withName("gerrit-init")
        .withImage(
            gerritCluster
                .getSpec()
                .getContainerImages()
                .getGerritImages()
                .getFullImageName("gerrit-init"))
        .withImagePullPolicy(gerritCluster.getSpec().getContainerImages().getImagePullPolicy())
        .withResources(gerritIndexer.getSpec().getResources())
        .withVolumeMounts(buildGerritInitVolumeMounts(gerritIndexer))
        .build();
  }

  private Container buildGerritIndexerContainer(
      GerritIndexer gerritIndexer, GerritCluster gerritCluster) {
    return new ContainerBuilder()
        .withName("gerrit-indexer")
        .withImage(
            gerritCluster
                .getSpec()
                .getContainerImages()
                .getGerritImages()
                .getFullImageName("gerrit-indexer"))
        .withImagePullPolicy(gerritCluster.getSpec().getContainerImages().getImagePullPolicy())
        .withArgs("--output", "/indexes")
        .withResources(gerritIndexer.getSpec().getResources())
        .withVolumeMounts(buildGerritIndexerVolumeMounts(gerritIndexer))
        .build();
  }

  private List<VolumeMount> buildGerritInitVolumeMounts(GerritIndexer gerritIndexer) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-init-config")
            .withMountPath("/var/config")
            .build());

    volumeMounts.addAll(buildCommonVolumeMounts(gerritIndexer));

    return volumeMounts;
  }

  private List<VolumeMount> buildGerritIndexerVolumeMounts(GerritIndexer gerritIndexer) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(outputVolumeName(gerritIndexer))
            .withSubPath(gerritIndexer.getSpec().getStorage().getOutput().getSubPath())
            .withMountPath("/indexes")
            .build());

    volumeMounts.addAll(buildCommonVolumeMounts(gerritIndexer));

    return volumeMounts;
  }

  private List<VolumeMount> buildCommonVolumeMounts(GerritIndexer gerritIndexer) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-config")
            .withMountPath("/var/mnt/etc/config")
            .build());

    String siteSubPath = gerritIndexer.getSpec().getStorage().getSite().getSubPath();
    volumeMounts.add(
        new VolumeMountBuilder()
            .withName("gerrit-site")
            .withMountPath("/var/gerrit")
            .withSubPath(siteSubPath)
            .build());

    String repoPath = gerritIndexer.getSpec().getStorage().getRepositories().getSubPath();
    VolumeMountBuilder repoVolumeMount =
        new VolumeMountBuilder()
            .withName(repoVolumeName(gerritIndexer))
            .withMountPath("/var/mnt/git")
            .withSubPath(repoPath)
            .withReadOnly(true);
    if (repoPath == null) {
      repoVolumeMount.withSubPath("git");
    } else {
      repoVolumeMount.withSubPath(repoPath);
    }
    volumeMounts.add(repoVolumeMount.build());
    return volumeMounts;
  }

  private List<Volume> buildVolumes(GerritIndexer gerritIndexer, GerritCluster gerritCluster) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        new VolumeBuilder()
            .withName("gerrit-site")
            .withNewPersistentVolumeClaim()
            .withClaimName(
                gerritIndexer.getSpec().getStorage().getSite().getPersistentVolumeClaim())
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

    if (isSeparateOutputVolume(gerritIndexer)) {
      volumes.add(
          new VolumeBuilder()
              .withName(repoVolumeName(gerritIndexer))
              .withNewPersistentVolumeClaim()
              .withClaimName(
                  gerritIndexer.getSpec().getStorage().getRepositories().getPersistentVolumeClaim())
              .endPersistentVolumeClaim()
              .build());
      volumes.add(
          new VolumeBuilder()
              .withName(outputVolumeName(gerritIndexer))
              .withNewPersistentVolumeClaim()
              .withClaimName(
                  gerritIndexer.getSpec().getStorage().getOutput().getPersistentVolumeClaim())
              .endPersistentVolumeClaim()
              .build());
    } else {
      volumes.add(
          new VolumeBuilder()
              .withName(outputVolumeName(gerritIndexer))
              .withNewPersistentVolumeClaim()
              .withClaimName(
                  gerritIndexer.getSpec().getStorage().getRepositories().getPersistentVolumeClaim())
              .endPersistentVolumeClaim()
              .build());
    }

    return volumes;
  }

  private String outputVolumeName(GerritIndexer gerritIndexer) {
    String outputVolumeName = "index-output";

    if (!isSeparateOutputVolume(gerritIndexer)) {
      outputVolumeName = "shared";
    }
    return outputVolumeName;
  }

  private String repoVolumeName(GerritIndexer gerritIndexer) {
    String repoVolumeName = "repositories";

    if (!isSeparateOutputVolume(gerritIndexer)) {
      repoVolumeName = "shared";
    }
    return repoVolumeName;
  }

  private boolean isSeparateOutputVolume(GerritIndexer gerritIndexer) {
    String outputPvc = gerritIndexer.getSpec().getStorage().getOutput().getPersistentVolumeClaim();

    return !outputPvc.equals(
        gerritIndexer.getSpec().getStorage().getRepositories().getPersistentVolumeClaim());
  }
}
