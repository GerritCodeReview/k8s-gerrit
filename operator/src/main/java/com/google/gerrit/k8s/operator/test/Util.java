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

package com.google.gerrit.k8s.operator.test;

import com.google.common.flogger.FluentLogger;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static KubernetesClient getKubernetesClient() {
    Config config;
    try {
      String kubeconfig = System.getenv("KUBECONFIG");
      if (kubeconfig != null) {
        config = Config.fromKubeconfig(Files.readString(Path.of(kubeconfig)));
        return new DefaultKubernetesClient(config);
      }
      logger.atWarning().log("KUBECONFIG variable not set. Using default config.");
    } catch (IOException e) {
      logger.atSevere().log("Failed to load kubeconfig. Trying default", e);
    }
    return new DefaultKubernetesClient();
  }
}
