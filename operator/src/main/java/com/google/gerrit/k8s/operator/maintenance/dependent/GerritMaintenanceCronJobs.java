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
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.api.model.maintenance.GitGcTask;
import com.google.gerrit.k8s.operator.util.KubernetesDependentCustomResource;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GerritMaintenanceCronJobs
    extends KubernetesDependentCustomResource<CronJob, GerritMaintenance>
    implements BulkDependentResource<CronJob, GerritMaintenance> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GerritMaintenanceCronJobs() {
    super(CronJob.class);
  }

  @Override
  public Map<String, CronJob> desiredResources(
      GerritMaintenance primary, Context<GerritMaintenance> context) {
    Map<String, CronJob> cronJobs = new HashMap<>();
    List<GitGcTask> gcTasks = primary.getSpec().getProjects().getGc();
    checkForConflict(gcTasks);
    for (GitGcTask gcTask : gcTasks) {
      cronJobs.put(gcTask.getName(), GitGarbageCollectionCronJob.desired(primary, gcTask, context));
    }
    return cronJobs;
  }

  @Override
  public Map<String, CronJob> getSecondaryResources(
      GerritMaintenance primary, Context<GerritMaintenance> context) {
    Set<CronJob> cronjobs = context.getSecondaryResources(CronJob.class);
    Map<String, CronJob> result = new HashMap<>(cronjobs.size());
    for (CronJob cj : cronjobs) {
      result.put(cj.getMetadata().getName(), cj);
    }
    return result;
  }

  private void checkForConflict(List<GitGcTask> gcTasks) {
    logger.atFine().log("Checking for conflicts in Git GC tasks");
    if (gcTasks.stream().filter(gc -> gc.getInclude().isEmpty()).count() > 1) {
      throw new GerritMaintenanceTaskConflictException(
          "Only a single GitGc working on all projects allowed per GerritMaintenance.");
    }

    Set<String> projectsWithMultipleGcTasks =
        gcTasks.stream()
            .map(gc -> gc.getInclude())
            .flatMap(Collection::stream)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(e -> e.getKey())
            .collect(Collectors.toSet());
    if (!projectsWithMultipleGcTasks.isEmpty()) {
      throw new GerritMaintenanceTaskConflictException(
          "Only a single Git GC task allowed per project. Projects with conflicts: "
              + String.join(", ", projectsWithMultipleGcTasks));
    }
  }
}
