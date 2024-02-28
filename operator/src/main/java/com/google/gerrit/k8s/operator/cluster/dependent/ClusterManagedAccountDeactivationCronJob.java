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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.shared.AccountDeactivationConfig;
import com.google.gerrit.k8s.operator.api.model.shared.BusyBoxImage;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.util.CRUDReconcileAddKubernetesDependentResource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobTemplateSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ClusterManagedAccountDeactivationCronJob
    extends CRUDReconcileAddKubernetesDependentResource<CronJob, Gerrit> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public ClusterManagedAccountDeactivationCronJob() {
    super(CronJob.class);
  }

  @Override
  protected CronJob desired(Gerrit gerrit, Context<Gerrit> context) {
    String ns = gerrit.getMetadata().getNamespace();
    String name = gerrit.getMetadata().getName();
    AccountDeactivationConfig config = gerrit.getSpec().getAccountDeactivation();
    String credentialSecretRef = config.getCredentialSecretRef();
    logger.atInfo().log("Reconciling account deactivation with name: %s/%s", ns, name);

    JobTemplateSpec jobTemplate =
        new JobTemplateSpecBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .withAnnotations(
                Map.of(
                    "sidecar.istio.io/inject",
                    "false",
                    "cluster-autoscaler.kubernetes.io/safe-to-evict",
                    "false"))
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("OnFailure")
            .addToContainers(buildAccountDeactivationContainer(config, gerrit))
            .withVolumes(getVolumes(gerrit))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    return new CronJobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withNamespace(ns)
        .withName(name)
        .withAnnotations(
            Collections.singletonMap("app.kubernetes.io/managed-by", "gerrit-operator"))
        .endMetadata()
        .withNewSpec()
        .withSchedule(config.getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(jobTemplate)
        .endSpec()
        .build();
  }

  private Container buildAccountDeactivationContainer(
      AccountDeactivationConfig config, Gerrit gerrit) {
    List<VolumeMount> volumeMounts = new ArrayList<>();

    BusyBoxImage busyBox = new BusyBoxImage();
    String ns = gerrit.getMetadata().getNamespace();
    String name = gerrit.getMetadata().getName();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));
    volumeMounts.add(GerritCluster.getLogsVolumeMount("/var/log/git"));

    String hostname =
        GerritService.getHostname(gerrit) + "a/config/server/deactivate.stale.accounts";
    String secretRef = config.getCredentialSecretRef();
    String username =
        client.secrets().inNamespace(ns).withName(secretRef).get().getData().get("username");
    String password =
        client.secrets().inNamespace(ns).withName(secretRef).get().getData().get("password");

    Container container =
        new ContainerBuilder()
            .withName("account-deactivation-container")
            .withImage(busyBox.getBusyBoxImage())
            .withCommand("curl", "--user", username, ":", password, "-X", "POST", hostname)
            .withVolumeMounts(volumeMounts)
            .build();
    return container;
  }

  private List<Volume> getVolumes(Gerrit gerrit) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        GerritCluster.getSharedVolume(
            gerrit.getSpec().getStorage().getSharedStorage().getExternalPVC()));
    return volumes;
  }
}
