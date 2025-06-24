package com.google.gerrit.k8s.operator.maintenance;

import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenanceSpec;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritProjectsTasks;
import com.google.gerrit.k8s.operator.api.model.maintenance.GitGcTask;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.apache.commons.lang3.RandomStringUtils;

public class GerritMaintenanceTestHelper {
  public static GerritMaintenance createGerritMaintenanceWithGitGcs(
      String namespace, List<Set<String>> projectSets) {
    GerritMaintenanceSpec spec = new GerritMaintenanceSpec();
    GerritProjectsTasks projectsTasks = new GerritProjectsTasks();
    List<GitGcTask> gcTasks = new ArrayList<>();
    for (Set<String> projects : projectSets) {
      GitGcTask gitGc = new GitGcTask();
      gitGc.setName(RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase());
      gitGc.setSchedule("0 0 * * *");
      gitGc.setInclude(projects);
      gcTasks.add(gitGc);
    }
    projectsTasks.setGc(gcTasks);
    spec.setProjects(projectsTasks);

    GerritMaintenance gm = new GerritMaintenance();
    gm.setMetadata(
        new ObjectMetaBuilder()
            .withName(RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase())
            .withNamespace(namespace)
            .build());
    gm.setSpec(spec);
    return gm;
  }
}
