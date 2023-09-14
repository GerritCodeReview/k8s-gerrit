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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GerritTest {

  @SuppressWarnings("unchecked")
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedGerritComponentsCreated(
      String inputFile, String expectedStatefulSet, String expectedService) {
    Context<Gerrit> context = (Context<Gerrit>) mock(Context.class);
    when(context.getSecondaryResource(StatefulSet.class)).thenReturn(Optional.empty());
    Gerrit input = ReconcilerUtils.loadYaml(Gerrit.class, this.getClass(), inputFile);
    GerritStatefulSet dependentStatefulSet = new GerritStatefulSet();
    assertThat(dependentStatefulSet.desired(input, context))
        .isEqualTo(
            ReconcilerUtils.loadYaml(StatefulSet.class, this.getClass(), expectedStatefulSet));

    GerritService dependentService = new GerritService();
    assertThat(dependentService.desired(input, null))
        .isEqualTo(ReconcilerUtils.loadYaml(Service.class, this.getClass(), expectedService));
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of("../gerrit_primary.yaml", "statefulset_primary.yaml", "service.yaml"));
  }
}
