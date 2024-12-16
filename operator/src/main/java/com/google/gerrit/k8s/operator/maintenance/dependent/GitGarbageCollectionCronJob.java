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

package com.google.gerrit.k8s.operator.maintenance.dependent;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.api.model.maintenance.GitGcTask;
import com.google.gerrit.k8s.operator.api.model.shared.ContainerImageConfig;
import com.google.gerrit.k8s.operator.api.model.shared.NfsWorkaroundConfig;
import com.google.gerrit.k8s.operator.cluster.GerritClusterLabelFactory;
import com.google.gerrit.k8s.operator.components.GerritSecurityContext;
import com.google.gerrit.k8s.operator.maintenance.GerritMaintenanceReconciler;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class GitGarbageCollectionCronJob {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected static CronJob desired(
      GerritMaintenance gerritMaintenance,
      GitGcTask gitGcTask,
      Context<GerritMaintenance> context) {
    String ns = gerritMaintenance.getMetadata().getNamespace();
    String name = gitGcTask.getName();
    logger.atInfo().log("Reconciling GitGcTask with name: %s/%s", ns, name);

    Map<String, String> gitGcLabels = getLabels(gerritMaintenance, gitGcTask.getName());
    List<Container> initContainers = new ArrayList<>();
    NfsWorkaroundConfig nfsWorkaround =
        gerritMaintenance.getSpec().getStorage().getStorageClasses().getNfsWorkaround();
    if (nfsWorkaround.isEnabled() && nfsWorkaround.isChownOnStartup()) {
      boolean hasIdmapdConfig =
          gerritMaintenance
                  .getSpec()
                  .getStorage()
                  .getStorageClasses()
                  .getNfsWorkaround()
                  .getIdmapdConfig()
              != null;
      ContainerImageConfig images = gerritMaintenance.getSpec().getContainerImages();
      initContainers.add(GerritCluster.createNfsInitContainer(hasIdmapdConfig, images));
    }

    JobTemplateSpec gitGcJobTemplate =
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
            .withLabels(gitGcLabels)
            .endMetadata()
            .withNewSpec()
            .withTolerations(gitGcTask.getTolerations())
            .withAffinity(gitGcTask.getAffinity())
            .addAllToImagePullSecrets(
                gerritMaintenance.getSpec().getContainerImages().getImagePullSecrets())
            .withRestartPolicy("OnFailure")
            .withSecurityContext(GerritSecurityContext.forPod())
            .addAllToInitContainers(initContainers)
            .addToContainers(buildGitGcContainer(gerritMaintenance, gitGcTask))
            .withVolumes(getVolumes(gerritMaintenance))
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    return new CronJobBuilder()
        .withApiVersion("batch/v1")
        .withNewMetadata()
        .withNamespace(ns)
        .withName(name)
        .withLabels(gitGcLabels)
        .endMetadata()
        .withNewSpec()
        .withSchedule(gitGcTask.getSchedule())
        .withConcurrencyPolicy("Forbid")
        .withJobTemplate(gitGcJobTemplate)
        .endSpec()
        .build();
  }

  private static Container buildGitGcContainer(
      GerritMaintenance gerritMaintenance, GitGcTask gitGcTask) {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(GerritCluster.getGitRepositoriesVolumeMount("/var/gerrit/git"));

    if (gerritMaintenance.getSpec().getStorage().getStorageClasses().getNfsWorkaround().isEnabled()
        && gerritMaintenance
                .getSpec()
                .getStorage()
                .getStorageClasses()
                .getNfsWorkaround()
                .getIdmapdConfig()
            != null) {
      volumeMounts.add(GerritCluster.getNfsImapdConfigVolumeMount());
    }

    ContainerBuilder gitGcContainerBuilder =
        new ContainerBuilder()
            .withName("git-gc")
            .withSecurityContext(GerritSecurityContext.forContainer())
            .withImagePullPolicy(
                gerritMaintenance.getSpec().getContainerImages().getImagePullPolicy())
            .withImage(
                gerritMaintenance
                    .getSpec()
                    .getContainerImages()
                    .getGerritImages()
                    .getFullImageName("gerrit-maintenance"))
            .withResources(gitGcTask.getResources())
            .withVolumeMounts(volumeMounts);

    ArrayList<String> args = new ArrayList<>();
    args.add("-d");
    args.add("/var/gerrit/git");
    args.add("projects");
    for (String project : gitGcTask.getInclude()) {
      args.add("--project");
      args.add(project);
    }
    for (String project : getExcludedProjects(gerritMaintenance, gitGcTask)) {
      args.add("--skip");
      args.add(project);
    }
    args.add("gc");
    String gitOpts = gitGcTask.getGitOptions();
    if (gitOpts != null && !gitOpts.isBlank()) {
      args.addAll(parseGitConfigOptsFromFile(gitGcTask.getGitOptions()));
    }
    args.addAll(gitGcTask.getArgs());
    gitGcContainerBuilder.addAllToArgs(args);

    return gitGcContainerBuilder.build();
  }

  private static List<Volume> getVolumes(GerritMaintenance gerritMaintenance) {
    List<Volume> volumes = new ArrayList<>();

    volumes.add(
        GerritCluster.getSharedVolume(
            gerritMaintenance.getSpec().getStorage().getSharedStorage().getExternalPVC()));

    if (gerritMaintenance
        .getSpec()
        .getStorage()
        .getStorageClasses()
        .getNfsWorkaround()
        .isEnabled()) {
      if (gerritMaintenance
              .getSpec()
              .getStorage()
              .getStorageClasses()
              .getNfsWorkaround()
              .getIdmapdConfig()
          != null) {
        volumes.add(GerritCluster.getNfsImapdConfigVolume());
      }
    }
    return volumes;
  }

  private static List<String> parseGitConfigOptsFromFile(String configFile) {
    List<String> opts = new ArrayList<>();
    Config cfg = new Config();
    try {
      cfg.fromText(configFile);
      for (String section : cfg.getSections()) {
        Set<String> subsections = cfg.getSubsections(section);
        if (subsections.isEmpty()) {
          for (String name : cfg.getNames(section)) {
            opts.add(formatGitCfgOption(section, null, name, cfg.getString(section, null, name)));
          }
        }
        for (String subsection : cfg.getSubsections(section)) {
          for (String name : cfg.getNames(section, subsection)) {
            opts.add(
                formatGitCfgOption(
                    section, subsection, name, cfg.getString(section, subsection, name)));
          }
        }
      }
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("Invalid git config in Git GC task.", e);
    }
    return opts;
  }

  private static String formatGitCfgOption(
      String section, String subsection, String name, String value) {
    if (subsection == null || subsection.isBlank()) {
      return String.format("-c %s.%s=%s", section, name, value);
    }
    return String.format("-c %s.%s.%s=%s", section, subsection, name, value);
  }

  private static String getComponentName(GerritMaintenance primary) {
    return String.format("gerrit-maintenance-%s-projects-gc", primary.getMetadata().getName());
  }

  private static Map<String, String> getLabels(GerritMaintenance primary, String taskName) {
    String name =
        String.format(
            "gerrit-maintenance-%s-projects-gc-%s", primary.getMetadata().getName(), taskName);
    return GerritClusterLabelFactory.create(
        name, getComponentName(primary), GerritMaintenanceReconciler.class.getSimpleName());
  }

  private static Set<String> getExcludedProjects(
      GerritMaintenance gerritMaintenance, GitGcTask gcTask) {
    Set<String> excludedProjects = gcTask.getExclude();

    if (gcTask.getInclude().isEmpty()) {
      List<GitGcTask> gitGcs = gerritMaintenance.getSpec().getProjects().getGc();
      gitGcs.remove(gcTask);
      for (GitGcTask gc : gitGcs) {
        excludedProjects.addAll(gc.getInclude());
      }
    }

    return excludedProjects;
  }
}
