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

package com.google.gerrit.k8s.operator.server;

import static com.google.gerrit.k8s.operator.server.FileSystemKeyStoreProvider.KEYSTORE_PATH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionMultisiteWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritClusterAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GerritClusterMultisiteAdmissionWebhook;
import com.google.gerrit.k8s.operator.admission.servlet.GitGcAdmissionWebhook;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import java.io.File;

public class ServerModule extends AbstractModule {

  private final ClusterMode clusterMode;

  public ServerModule(ClusterMode clusterMode) {
    this.clusterMode = clusterMode;
  }

  public void configure() {
    if (new File(KEYSTORE_PATH).exists()) {
      bind(KeyStoreProvider.class).to(FileSystemKeyStoreProvider.class);
    } else {
      bind(KeyStoreProvider.class).to(GeneratedKeyStoreProvider.class);
    }
    bind(HttpServer.class);
    Multibinder<AdmissionWebhookServlet> admissionWebhookServlets =
        Multibinder.newSetBinder(binder(), AdmissionWebhookServlet.class);
    if (clusterMode == ClusterMode.MULTISITE) {
      admissionWebhookServlets.addBinding().to(GerritClusterMultisiteAdmissionWebhook.class);
      admissionWebhookServlets.addBinding().to(GerritAdmissionMultisiteWebhook.class);
    } else {
      admissionWebhookServlets.addBinding().to(GerritClusterAdmissionWebhook.class);
      admissionWebhookServlets.addBinding().to(GitGcAdmissionWebhook.class);
      admissionWebhookServlets.addBinding().to(GerritAdmissionWebhook.class);
    }
  }
}
