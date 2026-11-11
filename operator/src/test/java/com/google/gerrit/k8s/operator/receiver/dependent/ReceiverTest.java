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

import static com.google.gerrit.k8s.operator.test.TestFixtures.RECEIVER_DEPLOYMENT_OPTIONS_PATCH;
import static com.google.gerrit.k8s.operator.test.TestFixtures.RECEIVER_OPTIONS_PATCH;

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.receiver.ReceiverReconciler;
import com.google.gerrit.k8s.operator.test.AbstractOperatorUnitTest;
import com.google.gerrit.k8s.operator.test.KubernetesResourceFixture;
import com.google.gerrit.k8s.operator.test.MockOperatorInstallation;
import com.google.gerrit.k8s.operator.test.TestFixtures;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ReceiverTest extends AbstractOperatorUnitTest {

  @ParameterizedTest
  @MethodSource("provideYamlManifests")
  public void expectedReceiverComponentsCreated(
      KubernetesResourceFixture<Receiver> inputFixture,
      KubernetesResourceFixture<Deployment> expectedDeploymentFixture,
      KubernetesResourceFixture<Service> expectedServiceFixture) {
    Receiver input = inputFixture.load();

    try (MockOperatorInstallation mock =
        new MockOperatorInstallation(client -> new ReceiverReconciler(client))) {
      mock.create(input);
      String ns = input.getMetadata().getNamespace();
      expectedDeploymentFixture.assertEqualTo(
          mock.awaitResource(Deployment.class, ns, expectedDeploymentFixture.getName()));
      expectedServiceFixture.assertEqualTo(
          mock.awaitResource(Service.class, ns, ReceiverService.getName(input)));
    }
  }

  private static Stream<Arguments> provideYamlManifests() {
    return Stream.of(
        Arguments.of(
            TestFixtures.receiver(RECEIVER_OPTIONS_PATCH),
            TestFixtures.receiverDeployment(RECEIVER_DEPLOYMENT_OPTIONS_PATCH),
            TestFixtures.receiverService()),
        Arguments.of(
            TestFixtures.receiver(),
            TestFixtures.receiverDeployment(),
            TestFixtures.receiverService()));
  }

  @Override
  protected ClusterMode getClusterMode() {
    return ClusterMode.HIGH_AVAILABILITY;
  }
}
