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

package com.google.gerrit.k8s.operator.config;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.network.IngressType;

public class OperatorContext {
  private static OperatorContext instance;

  private final ClusterMode clusterMode;
  private final IngressType ingressType;

  private OperatorContext(ClusterMode clusterMode, IngressType ingressType) {
    this.clusterMode = clusterMode;
    this.ingressType = ingressType;
  }

  protected static void createInstance(ClusterMode clusterMode, IngressType ingressType) {
    instance = new OperatorContext(clusterMode, ingressType);
  }

  public static ClusterMode getClusterMode() {
    if (instance == null) {
      throw new UnsupportedOperationException("The Operation Context must be initialised");
    }
    return instance.clusterMode;
  }

  public static IngressType getIngressType() {
    if (instance == null) {
      throw new UnsupportedOperationException("The Operation Context must be initialised");
    }
    return instance.ingressType;
  }

  public static String print() {
    StringBuilder output = new StringBuilder();
    output.append("Cluster Mode: " + instance.clusterMode + "\n");
    output.append("Ingress Type: " + instance.ingressType + "\n");
    return output.toString();
  }
}
