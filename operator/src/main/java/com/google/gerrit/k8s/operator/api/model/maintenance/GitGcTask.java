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

package com.google.gerrit.k8s.operator.api.model.maintenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GitGcTask extends GerritProjectsTask {
  private List<String> args = new ArrayList<>();
  private String gitOptions;

  public List<String> getArgs() {
    return args;
  }

  public void setArgs(List<String> args) {
    this.args = args;
  }

  public String getGitOptions() {
    return gitOptions;
  }

  public void setGitOptions(String gitOptions) {
    this.gitOptions = gitOptions;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(args, gitOptions);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    GitGcTask other = (GitGcTask) obj;
    return Objects.equals(args, other.args) && Objects.equals(gitOptions, other.gitOptions);
  }

  @Override
  public String toString() {
    return "GitGcTask [args=" + args + ", gitOptions=" + gitOptions + "]";
  }
}
