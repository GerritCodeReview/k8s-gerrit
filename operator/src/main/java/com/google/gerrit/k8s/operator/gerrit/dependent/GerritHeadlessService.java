// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.concurrent.TimeUnit;

@KubernetesDependent(resourceDiscriminator = GerritHeadlessServiceDiscriminator.class)
public class GerritHeadlessService extends GerritAbstractService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected Service desired(Gerrit gerrit, Context<Gerrit> context) {
    migrateFromHeadService(gerrit, context.getClient());
    return super.desired(gerrit, context);
  }

  // TODO: SHould be removed as soon as the migration is not required anymore.
  private void migrateFromHeadService(Gerrit gerrit, KubernetesClient client) {
    Resource<Service> serviceResource =
        client
            .resources(Service.class)
            .inNamespace(gerrit.getMetadata().getNamespace())
            .withName(getName(gerrit));
    Service service = serviceResource.get();
    if (service != null) {
      if (!service.getSpec().getClusterIP().equalsIgnoreCase("none")) {
        logger.atInfo().log(
            "Deleting old service (%s/%s) to replace it with headless service.",
            service.getMetadata().getNamespace(), service.getMetadata().getName());
        serviceResource.withTimeout(10, TimeUnit.SECONDS).delete();
      }
    }
  }

  @Override
  ServiceSpec getSpec(Gerrit gerrit) {
    return new ServiceSpecBuilder()
        .withClusterIP("None")
        .withPorts(GerritHeadlessService.getServicePorts(gerrit))
        .withSelector(GerritStatefulSet.getSelectorLabels(gerrit))
        .build();
  }

  @Override
  public String getName(String gerritName) {
    return gerritName;
  }
}
