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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.cluster.GerritClusterReconciler;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import com.google.gerrit.k8s.operator.gitgc.GitGarbageCollectionReconciler;
import com.google.gerrit.k8s.operator.receiver.ReceiverReconciler;
import com.google.gerrit.k8s.operator.server.HttpServer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;

public class GerritOperator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws Exception {
    Config config = new ConfigBuilder().withNamespace(null).build();
    KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
    Operator operator = new Operator(client);
    logger.atFine().log("Registering GerritCluster Reconciler");
    operator.register(new GerritClusterReconciler(client));
    logger.atFine().log("Registering GitGc Reconciler");
    operator.register(new GitGarbageCollectionReconciler(client));
    logger.atFine().log("Registering Gerrit Reconciler");
    operator.register(new GerritReconciler(client));
    logger.atFine().log("Registering Receiver Reconciler");
    operator.register(new ReceiverReconciler(client));
    operator.installShutdownHook();
    operator.start();

    Injector injector = Guice.createInjector(Stage.PRODUCTION, new OperatorModule());
    injector.getInstance(HttpServer.class).start();
  }
}
