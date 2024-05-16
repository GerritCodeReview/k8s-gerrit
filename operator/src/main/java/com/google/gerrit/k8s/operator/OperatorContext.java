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

package com.google.gerrit.k8s.operator;

import com.google.gerrit.k8s.operator.api.model.Constants.ClusterMode;

public class OperatorContext {
  private static OperatorContext instance;

  private final ClusterMode clusterMode;

  private OperatorContext(ClusterMode clusterMode) {
    this.clusterMode = clusterMode;
  }

  public static void createInstance(ClusterMode clusterMode) {
    if (instance == null) {
      instance = new OperatorContext(clusterMode);
    }
  }

  public static ClusterMode getClusterMode() {
    if (instance == null) {
      throw new UnsupportedOperationException("The Operation Context must be initialised");
    }
    return instance.clusterMode;
  }
}
