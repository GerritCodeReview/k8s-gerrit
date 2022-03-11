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

package com.google.gerrit.k8s.operator;

import io.fabric8.kubernetes.api.model.ResourceRequirements;

public class GitGcSpec {
  private String image;
  private String schedule;
  private ResourceRequirements resources;
  private String repositoryPVC;
  private String logPVC;

  public GitGcSpec() {
    image = "k8s-gerrit/git-gc";
    resources = new ResourceRequirements();
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getImage() {
    return image;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setResources(ResourceRequirements resources) {
    this.resources = resources;
  }

  public ResourceRequirements getResources() {
    return resources;
  }

  public void setRepositoryPVC(String repositoryPVC) {
    this.repositoryPVC = repositoryPVC;
  }

  public String getRepositoryPVC() {
    return repositoryPVC;
  }

  public void setLogPVC(String logPVC) {
    this.logPVC = logPVC;
  }

  public String getLogPVC() {
    return logPVC;
  }
}
