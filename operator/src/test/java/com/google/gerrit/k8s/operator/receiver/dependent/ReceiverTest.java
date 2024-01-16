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

package com.google.gerrit.k8s.operator.receiver.dependent;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ReceiverTest {
  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedReceiverComponentsCreated(
      String inputFile, String expectedDeployment, String expectedService) {
    Receiver input = ReconcilerUtils.loadYaml(Receiver.class, this.getClass(), inputFile);
    ReceiverDeployment dependentDeployment = new ReceiverDeployment();
    assertThat(dependentDeployment.desired(input, null))
        .isEqualTo(ReconcilerUtils.loadYaml(Deployment.class, this.getClass(), expectedDeployment));

    ReceiverService dependentService = new ReceiverService();
    assertThat(dependentService.desired(input, null))
        .isEqualTo(ReconcilerUtils.loadYaml(Service.class, this.getClass(), expectedService));
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of("../receiver.yaml", "deployment.yaml", "service.yaml"),
        Arguments.of("../receiver_minimal.yaml", "deployment_minimal.yaml", "service.yaml"));
  }
}
