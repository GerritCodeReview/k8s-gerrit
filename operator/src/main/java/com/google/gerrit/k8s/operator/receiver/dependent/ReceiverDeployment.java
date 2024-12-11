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

package com.google.gerrit.k8s.operator.receiver.dependent;

import static com.google.gerrit.k8s.operator.Constants.GERRIT_USER_GROUP_ID;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.receiver.ReceiverReconciler;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@KubernetesDependent
public class ReceiverDeployment
    extends CRUDReconcileAddKubernetesDependentResource<Deployment, Receiver> {
  public static final int HTTP_PORT = 8080;

  public ReceiverDeployment() {
    super(Deployment.class);
  }

  @Override
  protected Deployment desired(Receiver receiver, Context<Receiver> context) {
    DeploymentBuilder deploymentBuilder = new DeploymentBuilder();

    List<Container> initContainers = new ArrayList<>();

    NfsWorkaroundConfig nfsWorkaround =
        receiver.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      initContainers.add(
          GerritCluster.createNfsInitContainer(
              receiver
                      .getSpec()
                      .getStorage()
                      .getStorageClasses()
                      .getNfsWorkaround()
                      .getIdmapdConfig()
                  != null,
              receiver.getSpec().getContainerImages()));
    }

    deploymentBuilder
        .withApiVersion("apps/v1")
        .withNewMetadata()
        .withName(receiver.getMetadata().getName())
        .withNamespace(receiver.getMetadata().getNamespace())
        .withLabels(getLabels(receiver))
        .endMetadata()
        .withNewSpec()
        .withReplicas(receiver.getSpec().getReplicas())
        .withNewStrategy()
        .withNewRollingUpdate()
        .withMaxSurge(receiver.getSpec().getMaxSurge())
        .withMaxUnavailable(receiver.getSpec().getMaxUnavailable())
        .endRollingUpdate()
        .endStrategy()
        .withNewSelector()
        .withMatchLabels(getSelectorLabels(receiver))
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(getLabels(receiver))
        .endMetadata()
        .withNewSpec()
        .withTolerations(receiver.getSpec().getTolerations())
        .withTopologySpreadConstraints(receiver.getSpec().getTopologySpreadConstraints())
        .withAffinity(receiver.getSpec().getAffinity())
        .withPriorityClassName(receiver.getSpec().getPriorityClassName())
        .addAllToImagePullSecrets(receiver.getSpec().getContainerImages().getImagePullSecrets())
        .withNewSecurityContext()
        .withFsGroup(GERRIT_USER_GROUP_ID)
        .endSecurityContext()
        .addAllToInitContainers(initContainers)
        .addNewContainer()
        .withName("apache-git-http-backend")
        .withImagePullPolicy(receiver.getSpec().getContainerImages().getImagePullPolicy())
        .withImage(
            receiver
                .getSpec()
                .getContainerImages()
                .getGerritImages()
                .getFullImageName("apache-git-http-backend"))
        .withPorts(getContainerPorts(receiver))
        .withResources(receiver.getSpec().getResources())
        .withReadinessProbe(receiver.getSpec().getReadinessProbe())
        .withLivenessProbe(receiver.getSpec().getLivenessProbe())
        .addAllToVolumeMounts(getVolumeMounts(receiver, false))
        .endContainer()
        .addAllToVolumes(getVolumes(receiver))
        .endSpec()
        .endTemplate()
        .endSpec();

    return deploymentBuilder.build();
  }

  private static String getComponentName(Receiver receiver) {
    return String.format("receiver-deployment-%s", receiver.getMetadata().getName());
  }

  public static Map<String, String> getSelectorLabels(Receiver receiver) {
    return GerritClusterLabelFactory.createSelectorLabels(
        receiver.getMetadata().getName(), getComponentName(receiver));
  }

  public static Map<String, String> getLabels(Receiver receiver) {
    return GerritClusterLabelFactory.create(
        receiver.getMetadata().getName(),
        getComponentName(receiver),
        ReceiverReconciler.class.getSimpleName());
  }

  private Set<Volume> getVolumes(Receiver receiver) {
    Set<Volume> volumes = new HashSet<>();
    volumes.add(
        GerritCluster.getSharedVolume(
            receiver.getSpec().getStorage().getSharedStorage().getExternalPVC()));

    volumes.add(
        new VolumeBuilder()
            .withName(receiver.getSpec().getCredentialSecretRef())
            .withNewSecret()
            .withSecretName(receiver.getSpec().getCredentialSecretRef())
            .endSecret()
            .build());

    NfsWorkaroundConfig nfsWorkaround =
        receiver.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumes.add(GerritCluster.getNfsImapdConfigVolume());
    }

    return volumes;
  }

  private Set<VolumeMount> getVolumeMounts(Receiver receiver, boolean isInitContainer) {
    Set<VolumeMount> volumeMounts = new HashSet<>();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));

    volumeMounts.add(
        new VolumeMountBuilder()
            .withName(receiver.getSpec().getCredentialSecretRef())
            .withMountPath("/var/apache/credentials/.htpasswd")
            .withSubPath(".htpasswd")
            .build());

    NfsWorkaroundConfig nfsWorkaround =
        receiver.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.getIdmapdConfig() != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    return volumeMounts;
  }

  private List<ContainerPort> getContainerPorts(Receiver receiver) {
    List<ContainerPort> containerPorts = new ArrayList<>();
    containerPorts.add(new ContainerPort(HTTP_PORT, null, null, "http", null));
    return containerPorts;
  }
}
