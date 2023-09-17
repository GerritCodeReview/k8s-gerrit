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

import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING_GET_REPLICA;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING_POST_REPLICA;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING_PRIMARY;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_MAPPING_RECEIVER;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.GERRIT_TLS_CONTEXT;
import static com.google.gerrit.k8s.operator.network.ambassador.GerritAmbassadorReconciler.MAPPING_EVENT_SOURCE;

import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMapping;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingGETReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPOSTReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPrimary;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiver;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterTLSContext;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.LoadBalanceCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.ReceiverMappingCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.SingleMappingCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.TLSContextCondition;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.inject.Singleton;
import io.getambassador.v2.Mapping;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.HashMap;
import java.util.Map;

/*

  Case 1, 2) 0 primary 1 replica || 1 primary 0 replica

  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-mapping
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: /
  prefix_regex: false
  service: gerrit||gerrit-replica.gerrit-operator:8080

  Case 3) 1 primary 1 replica

  # Send fetch/clone POST requests to replica
  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-mapping-post-replica
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: / .* /git-upload-pack
  prefix_regex: true
  service: gerrit-replica.gerrit-operator:8080

  # Send fetch/clone GET requests to replica
  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-mapping-get-replica
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: /
  prefix_regex: false
  query_parameters:
    service: git-upload-pack
  service: gerrit-replica.gerrit-operator:8080

  # Send all others to primary
  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-mapping-primary
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: /
  prefix_regex: false
  service: gerrit.gerrit-operator:8080

  ---
  apiVersion: getambassador.io/v2
  kind: TLSContext
  ambassador_id:
  - internal_proxy
  name: my-name
  secret: example.certs
  hosts:
    - my-name
  secret_namespacing: true


*/

/*
ingress:
  enabled: true
  host: example.com
  annotations: {}
  tls:
    enabled: false
    secret: ""
  ambassador_id:
    - internal_proxy
    - another_id
*/

// If only one Gerrit instance in GerritCluster, send all git-over-https requests to it.
// Otherwise, send git `fetch/clone` requests to the replica and `push` requests to the primary
// instance.

/**
 * git fetch/clone result in two HTTP requests to the git server: - POST
 * /my-test-repo/git-upload-pack - GET /my-test-repo/info/refs?service=git-upload-pack
 */
@Singleton
@ControllerConfiguration(
    namespaces = "gerrit-operator",
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
          name = GERRIT_TLS_CONTEXT,
          type = GerritClusterTLSContext.class,
          reconcilePrecondition = TLSContextCondition.class),
    })
public class GerritAmbassadorReconciler
    implements Reconciler<GerritNetwork>, EventSourceInitializer<GerritNetwork> {

  public static final String MAPPING_EVENT_SOURCE = "mapping-event-source";
  public static final String GERRIT_MAPPING = "gerrit-mapping";
  public static final String GERRIT_MAPPING_POST_REPLICA = "gerrit-mapping-post-replica";
  public static final String GERRIT_MAPPING_GET_REPLICA = "gerrit-mapping-get-replica";
  public static final String GERRIT_MAPPING_PRIMARY = "gerrit-mapping-primary";
  public static final String GERRIT_MAPPING_RECEIVER = "gerrit-mapping-receiver";
  public static final String GERRIT_TLS_CONTEXT = "gerrit-tls-context";

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<GerritNetwork> context) {
    InformerEventSource<Mapping, GerritNetwork> mappingEventSource =
        new InformerEventSource<>(
            InformerConfiguration.from(Mapping.class, context).build(), context);

    Map<String, EventSource> eventSources = new HashMap<>();
    eventSources.put(MAPPING_EVENT_SOURCE, mappingEventSource);
    return eventSources;
  }

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {

    return UpdateControl.noUpdate();
  }
}
