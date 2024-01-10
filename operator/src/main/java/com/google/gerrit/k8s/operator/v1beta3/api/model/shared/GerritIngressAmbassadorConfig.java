// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.v1beta3.api.model.shared;

import java.util.List;

@Deprecated
public class GerritIngressAmbassadorConfig {
  private List<String> id;
  private boolean createHost;

  public List<String> getId() {
    return this.id;
  }

  public void setId(List<String> id) {
    this.id = id;
  }

  public boolean getCreateHost() {
    return this.createHost;
  }

  public void setCreateHost(boolean createHost) {
    this.createHost = createHost;
  }
}
