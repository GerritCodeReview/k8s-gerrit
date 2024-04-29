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

package com.google.gerrit.k8s.operator.cluster;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import java.util.HashMap;
import java.util.Map;

public class GerritClusterLabelFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Map<String, String> create(
      GerritCluster cluster, String component, String createdBy) {
    return create(cluster.getMetadata().getName(), component, createdBy);
  }

  public static Map<String, String> create(String instance, String component, String createdBy) {
    Map<String, String> labels = new HashMap<>();

    labels.putAll(createSelectorLabels(instance, component));
    labels.put("app.kubernetes.io/version", version());
    labels.put("app.kubernetes.io/created-by", createdBy);

    return labels;
  }

  public static Map<String, String> createSelectorLabels(String instance, String component) {
    Map<String, String> labels = new HashMap<>();

    labels.put("app.kubernetes.io/name", "gerrit");
    labels.put("app.kubernetes.io/instance", instance);
    labels.put("app.kubernetes.io/component", component);
    labels.put("app.kubernetes.io/part-of", instance);
    labels.put("app.kubernetes.io/managed-by", "gerrit-operator");

    return labels;
  }

  private static String version() {
    String version = GerritCluster.class.getPackage().getImplementationVersion();
    if (version == null || version.isBlank()) {
      logger.atWarning().log("Unable to read Gerrit Operator version from jar.");
      version = "unknown";
    }
    return version;
  }
}
