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

package com.google.gerrit.k8s.operator.admission.servlet;

import com.google.gerrit.k8s.operator.Constants;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.api.model.maintenance.GitGcTask;
import com.google.gerrit.k8s.operator.maintenance.dependent.GerritMaintenanceCronJobs;
import com.google.gerrit.k8s.operator.maintenance.dependent.GerritMaintenanceTaskConflictException;
import com.google.gerrit.k8s.operator.server.ValidatingAdmissionWebhookServlet;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class GerritMaintenanceAdmissionWebhook extends ValidatingAdmissionWebhookServlet {
  private static final long serialVersionUID = 1L;

  @Override
  public Status validate(HasMetadata resource) {
    if (!(resource instanceof GerritMaintenance)) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage("Invalid resource. Expected GerritMaintenance-resource for validation.")
          .build();
    }

    GerritMaintenance gm = (GerritMaintenance) resource;
    List<GitGcTask> gcTasks = gm.getSpec().getProjects().getGc();
    try {
      GerritMaintenanceCronJobs.checkForConflict(gcTasks);
    } catch (GerritMaintenanceTaskConflictException e) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_CONFLICT)
          .withMessage(e.getMessage())
          .build();
    }

    try {
      Config cfg = new Config();
      for (GitGcTask gcTask : gcTasks) {
        String gitOptions = gcTask.getGitOptions();
        if (gitOptions != null) {
          cfg.fromText(gcTask.getGitOptions());
        }
      }
    } catch (ConfigInvalidException e) {
      return new StatusBuilder()
          .withCode(HttpServletResponse.SC_BAD_REQUEST)
          .withMessage(e.getMessage())
          .build();
    }

    return new StatusBuilder().withCode(HttpServletResponse.SC_OK).build();
  }

  @Override
  public String getName() {
    return Constants.GERRIT_MAINTENANCE_KIND;
  }
}
