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

package com.google.gerrit.k8s.operator.receiver;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverDeployment;
import com.google.gerrit.k8s.operator.receiver.dependent.ReceiverService;
import com.google.gerrit.k8s.operator.v1beta2.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.v1beta2.api.model.receiver.ReceiverStatus;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@ControllerConfiguration(
    dependents = {
      @Dependent(name = "receiver-deployment", type = ReceiverDeployment.class),
      @Dependent(
          name = "receiver-service",
          type = ReceiverService.class,
          dependsOn = {"receiver-deployment"})
    })
public class ReceiverReconciler implements Reconciler<Receiver>, EventSourceInitializer<Receiver> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SECRET_EVENT_SOURCE_NAME = "secret-event-source";
  private final KubernetesClient client;

  @Inject
  public ReceiverReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Receiver> context) {
    final SecondaryToPrimaryMapper<Secret> secretMapper =
        (Secret secret) ->
            context
                .getPrimaryCache()
                .list(
                    receiver ->
                        receiver
                            .getSpec()
                            .getCredentialSecretRef()
                            .equals(secret.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSource<Secret, Receiver> secretEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Secret.class, context)
                .withSecondaryToPrimaryMapper(secretMapper)
                .build(),
            context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(SECRET_EVENT_SOURCE_NAME, secretEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<Receiver> reconcile(Receiver receiver, Context<Receiver> context)
      throws Exception {
    if (receiver.getStatus() != null && isReceiverRestartRequired(receiver, context)) {
      restartReceiverDeployment(receiver);
    }

    return UpdateControl.patchStatus(updateStatus(receiver, context));
  }

  void restartReceiverDeployment(Receiver receiver) {
    logger.atInfo().log(
        "Restarting Receiver %s due to configuration change.", receiver.getMetadata().getName());
    client
        .apps()
        .deployments()
        .inNamespace(receiver.getMetadata().getNamespace())
        .withName(receiver.getMetadata().getName())
        .rolling()
        .restart();
  }

  private Receiver updateStatus(Receiver receiver, Context<Receiver> context) {
    ReceiverStatus status = receiver.getStatus();
    if (status == null) {
      status = new ReceiverStatus();
    }

    Secret sec =
        client
            .secrets()
            .inNamespace(receiver.getMetadata().getNamespace())
            .withName(receiver.getSpec().getCredentialSecretRef())
            .get();

    if (sec != null) {
      status.setAppliedCredentialSecretVersion(sec.getMetadata().getResourceVersion());
    }

    receiver.setStatus(status);
    return receiver;
  }

  private boolean isReceiverRestartRequired(Receiver receiver, Context<Receiver> context) {
    String secVersion =
        client
            .secrets()
            .inNamespace(receiver.getMetadata().getNamespace())
            .withName(receiver.getSpec().getCredentialSecretRef())
            .get()
            .getMetadata()
            .getResourceVersion();
    String appliedSecVersion = receiver.getStatus().getAppliedCredentialSecretVersion();
    if (!secVersion.equals(appliedSecVersion)) {
      logger.atFine().log(
          "Looking up Secret: %s; Installed secret resource version: %s; Resource version known to the Receiver: %s",
          receiver.getSpec().getCredentialSecretRef(), secVersion, appliedSecVersion);
      return true;
    }
    return false;
  }
}
