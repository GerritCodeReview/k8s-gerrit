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

package com.google.gerrit.k8s.operator.gerrit.dependent;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.GerritReconciler;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GenericRetryExecution;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public class GerritTest {
  @Rule public KubernetesServer kubernetesServer = new KubernetesServer();

  @BeforeAll
  public void setup() throws Exception {
    kubernetesServer.before();
  }

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritComponentsCreated(
      String inputFile,
      String expectedGerritConfigMapOutputFile,
      String expectedGerritInitConfigMapOutputFile,
      String expectedStatefulSetOutputFile,
      String expectedServiceOutputFile) {
    Gerrit gerrit = ReconcilerUtils.loadYaml(Gerrit.class, this.getClass(), inputFile);
    Context<Gerrit> context =
        getContext(new GerritReconciler(kubernetesServer.getClient()), gerrit);

    GerritConfigMap gerritCmDependent = new GerritConfigMap();
    ConfigMap gerritCmResult = gerritCmDependent.desired(gerrit, context);
    ConfigMap expectedGerritCm =
        ReconcilerUtils.loadYaml(
            ConfigMap.class, this.getClass(), expectedGerritConfigMapOutputFile);
    assertThat(gerritCmResult.getMetadata()).isEqualTo(expectedGerritCm.getMetadata());
    assertThat(gerritCmResult.getData().keySet()).isEqualTo(expectedGerritCm.getData().keySet());
    for (Map.Entry<String, String> file : gerritCmResult.getData().entrySet()) {
      assertThat(file.getValue().replaceAll("\\s+", "").trim())
          .isEqualTo(expectedGerritCm.getData().get(file.getKey()).replaceAll("\\s+", "").trim());
    }

    GerritInitConfigMap gerritInitCmDependent = new GerritInitConfigMap();
    ConfigMap gerritInitCmResult = gerritInitCmDependent.desired(gerrit, context);
    ConfigMap expectedGerritInitCm =
        ReconcilerUtils.loadYaml(
            ConfigMap.class, this.getClass(), expectedGerritInitConfigMapOutputFile);
    assertThat(gerritInitCmResult.getMetadata()).isEqualTo(expectedGerritInitCm.getMetadata());
    assertThat(gerritInitCmResult.getData().keySet())
        .isEqualTo(expectedGerritInitCm.getData().keySet());
    for (Map.Entry<String, String> file : gerritInitCmResult.getData().entrySet()) {
      assertThat(file.getValue().replaceAll("\\s+", "").trim())
          .isEqualTo(
              expectedGerritInitCm.getData().get(file.getKey()).replaceAll("\\s+", "").trim());
    }

    GerritStatefulSet stsDependent = new GerritStatefulSet();
    StatefulSet stsResult = stsDependent.desired(gerrit, context);
    StatefulSet expectedSts =
        ReconcilerUtils.loadYaml(StatefulSet.class, this.getClass(), expectedStatefulSetOutputFile);
    assertThat(stsResult).isEqualTo(expectedSts);

    GerritService serviceDependent = new GerritService();
    Service serviceResult = serviceDependent.desired(gerrit, context);
    Service expectedService =
        ReconcilerUtils.loadYaml(Service.class, this.getClass(), expectedServiceOutputFile);
    assertThat(serviceResult).isEqualTo(expectedService);
  }

  private Context<Gerrit> getContext(Reconciler<Gerrit> reconciler, Gerrit primary) {
    Controller<Gerrit> controller =
        new Controller<Gerrit>(
            reconciler,
            new BaseConfigurationService().getConfigurationFor(reconciler),
            kubernetesServer.getClient());

    return new DefaultContext<Gerrit>(
        new GenericRetryExecution(new GenericRetry()), controller, primary);
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            "../gerrit_single_primary.yaml",
            "gerrit_configmap_single_primary.yaml",
            "gerrit-init_configmap_single_primary.yaml",
            "statefulset_single_primary.yaml",
            "service.yaml"),
        Arguments.of(
            "../gerrit_ha_primary.yaml",
            "gerrit_configmap_ha_primary.yaml",
            "gerrit-init_configmap_ha_primary.yaml",
            "statefulset_ha_primary.yaml",
            "service.yaml"));
  }
}
