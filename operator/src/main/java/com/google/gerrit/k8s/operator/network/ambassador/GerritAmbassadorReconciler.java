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

package com.google.gerrit.k8s.operator.network.ambassador;

import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.MAPPING_EVENT_SOURCE;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterHost.GERRIT_HOST;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMapping.GERRIT_MAPPING;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingGETReplica.GERRIT_MAPPING_GET_REPLICA;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPOSTReplica.GERRIT_MAPPING_POST_REPLICA;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPrimary.GERRIT_MAPPING_PRIMARY;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiver.GERRIT_MAPPING_RECEIVER;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiverGET.GERRIT_MAPPING_RECEIVER_GET;
import static com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterTLSContext.GERRIT_TLS_CONTEXT;

import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.CreateHostCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterHost;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMapping;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingGETReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPOSTReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPrimary;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiver;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiverGET;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterTLSContext;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.LoadBalanceCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.ReceiverGETMappingCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.ReceiverMappingCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.SingleMappingCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.TLSContextCondition;
import com.google.inject.Singleton;
import io.getambassador.v2.Mapping;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides an Ambassador-based implementation for GerritNetworkReconciler.
 *
 * <p>Creates and manages Ambassador Custom Resources using the "managed dependent resources"
 * approach in josdk. Since multiple dependent resources of the same type (`Mapping`) need to be
 * created, "resource discriminators" are used for each of the different Mapping dependent
 * resources.
 *
 * <p>Ambassador custom resource POJOs are generated via the `java-generator-maven-plugin` in the
 * fabric8 project.
 *
 * <p>Mapping logic
 *
 * <p>The Mappings are created based on the composition of Gerrit instances in the GerritCluster.
 *
 * <p>There are three cases:
 *
 * <p>1. 0 Primary 1 Replica
 *
 * <p>Direct all traffic (read/write) to the Replica
 *
 * <p>2. 1 Primary 0 Replica
 *
 * <p>Direct all traffic (read/write) to the Primary
 *
 * <p>3. 1 Primary 1 Replica
 *
 * <p>Direct write traffic to Primary and read traffic to Replica. To capture this requirement,
 * three different Mappings have to be created.
 *
 * <p>Note: git fetch/clone operations result in two HTTP requests to the git server. The first is
 * of the form `GET /my-test-repo/info/refs?service=git-upload-pack` and the second is of the form
 * `POST /my-test-repo/git-upload-pack`.
 *
 * <p>Note: git push operations result in two HTTP requests to the git server. The first is of the
 * form `GET /my-test-repo/info/refs?service=git-receive-pack` and the second is of the form `POST
 * /my-test-repo/git-receive-pack`.
 *
 * <p>If a Receiver is part of the GerritCluster, additional mappings are created such that all
 * requests that the replication plugin sends to the `adminUrl` [1] are routed to the Receiver. This
 * includes `git push` related `GET` and `POST` requests, and requests to the `/projects` REST API
 * endpoints.
 *
 * <p>[1]
 * https://gerrit.googlesource.com/plugins/replication/+/refs/heads/master/src/main/resources/Documentation/config.md
 */
@Singleton
@Workflow(
    dependents = {
      @Dependent(
          name = GERRIT_MAPPING,
          type = GerritClusterMapping.class,
          // Cluster has only either Primary or Replica instance
          reconcilePrecondition = SingleMappingCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_MAPPING_POST_REPLICA,
          type = GerritClusterMappingPOSTReplica.class,
          // Cluster has both Primary and Replica instances
          reconcilePrecondition = LoadBalanceCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_MAPPING_GET_REPLICA,
          type = GerritClusterMappingGETReplica.class,
          reconcilePrecondition = LoadBalanceCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_MAPPING_PRIMARY,
          type = GerritClusterMappingPrimary.class,
          reconcilePrecondition = LoadBalanceCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_MAPPING_RECEIVER,
          type = GerritClusterMappingReceiver.class,
          reconcilePrecondition = ReceiverMappingCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_MAPPING_RECEIVER_GET,
          type = GerritClusterMappingReceiverGET.class,
          reconcilePrecondition = ReceiverGETMappingCondition.class,
          useEventSourceWithName = MAPPING_EVENT_SOURCE),
      @Dependent(
          name = GERRIT_TLS_CONTEXT,
          type = GerritClusterTLSContext.class,
          reconcilePrecondition = TLSContextCondition.class),
      @Dependent(
          name = GERRIT_HOST,
          type = GerritClusterHost.class,
          reconcilePrecondition = CreateHostCondition.class),
    })
public class GerritAmbassadorReconciler implements Reconciler<GerritNetwork> {

  public static final String MAPPING_EVENT_SOURCE = "mapping-event-source";

  // Because we have multiple dependent resources of the same type `Mapping`, we need to specify
  // a named event source.
  @Override
  public List<EventSource<?, GerritNetwork>> prepareEventSources(
      EventSourceContext<GerritNetwork> context) {
    InformerEventSource<Mapping, GerritNetwork> mappingEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(Mapping.class, GerritNetwork.class).build(),
            context);

    List<EventSource<?, GerritNetwork>> eventSources = new ArrayList<>();
    eventSources.add(mappingEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {
    return UpdateControl.noUpdate();
  }
}
