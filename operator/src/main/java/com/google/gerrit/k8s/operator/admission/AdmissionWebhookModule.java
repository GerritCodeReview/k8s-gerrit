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

package com.google.gerrit.k8s.operator.admission;

import com.google.gerrit.k8s.operator.admission.validators.GerritClusterValidator;
import com.google.gerrit.k8s.operator.admission.validators.GerritValidator;
import com.google.gerrit.k8s.operator.admission.validators.GitGcValidator;
import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import io.javaoperatorsdk.webhook.admission.validation.Validator;

public class AdmissionWebhookModule extends AbstractModule {
  public void configure() {
    install(new FactoryModuleBuilder().build(ValidationWebhookConfigApplier.Factory.class));

    bind(ValidationWebhookConfigs.class);

    bind(new TypeLiteral<Validator<Gerrit>>() {}).to(GerritValidator.class);
    bind(new TypeLiteral<Validator<GerritCluster>>() {}).to(GerritClusterValidator.class);
    bind(new TypeLiteral<Validator<GitGarbageCollection>>() {}).to(GitGcValidator.class);
  }
}
