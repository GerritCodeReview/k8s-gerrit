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

package com.google.gerrit.k8s.operator.api.model.cluster;

import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenanceSpecTemplate;
import com.google.gerrit.k8s.operator.api.model.tasks.incomingrepl.IncomingReplicationTaskTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScheduledTasks {
  private List<IncomingReplicationTaskTemplate> incomingReplication = new ArrayList<>();
  private GerritMaintenanceSpecTemplate gerritMaintenance;

  public List<IncomingReplicationTaskTemplate> getIncomingReplication() {
    return incomingReplication;
  }

  public void setIncomingReplication(List<IncomingReplicationTaskTemplate> incomingReplTasks) {
    this.incomingReplication = incomingReplTasks;
  }

  public GerritMaintenanceSpecTemplate getGerritMaintenance() {
    return gerritMaintenance;
  }

  public void setGerritMaintenance(GerritMaintenanceSpecTemplate gerritMaintenance) {
    this.gerritMaintenance = gerritMaintenance;
  }

  @Override
  public int hashCode() {
    return Objects.hash(gerritMaintenance, incomingReplication);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ScheduledTasks other = (ScheduledTasks) obj;
    return Objects.equals(gerritMaintenance, other.gerritMaintenance)
        && Objects.equals(incomingReplication, other.incomingReplication);
  }

  @Override
  public String toString() {
    return "ScheduledTasks [incomingReplication="
        + incomingReplication
        + ", gerritMaintenance="
        + gerritMaintenance
        + "]";
  }
}
