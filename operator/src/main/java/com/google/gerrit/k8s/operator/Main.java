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

package com.google.gerrit.k8s.operator;

import com.google.gerrit.k8s.operator.admission.ValidationWebhookConfigs;
import com.google.gerrit.k8s.operator.server.HttpServer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
// import io.getambassador.v3alpha1.MappingBuilder;

public class Main {

  public static void main(String[] args) throws Exception {
    //    Mapping m =
    //        new MappingBuilder()
    //            .withApiVersion(null)
    //            .withNewMetadata()
    //            .endMetadata()
    //            .withNewSpec()
    //            .withBypassAuth(true)
    //            .withAmbassadorId(Arrays.asList("ambassador_id"))
    //            .endSpec()
    //            .build();

    Injector injector = Guice.createInjector(Stage.PRODUCTION, new OperatorModule());
    injector.getInstance(HttpServer.class).start();
    injector.getInstance(ValidationWebhookConfigs.class).apply();
    injector.getInstance(GerritOperator.class).start();
  }
}
