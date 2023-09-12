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

import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMapping;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingGETReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPOSTReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPrimary;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.LoadBalanceCondition;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.SingleMappingCondition;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import com.google.inject.Singleton;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

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
          name = "gerrit-mapping",
          type = GerritClusterMapping.class,
          // Cluster has only either Primary or Replica instance
          reconcilePrecondition = SingleMappingCondition.class),
      @Dependent(
          name = "gerrit-mapping-post-replica",
          type = GerritClusterMappingPOSTReplica.class,
          // Cluster has both Primary and Replica instances
          reconcilePrecondition = LoadBalanceCondition.class),
      @Dependent(
          name = "gerrit-mapping-get-replica",
          type = GerritClusterMappingGETReplica.class,
          reconcilePrecondition = LoadBalanceCondition.class),
      @Dependent(
          name = "gerrit-mapping-primary",
          type = GerritClusterMappingPrimary.class,
          reconcilePrecondition = LoadBalanceCondition.class)
    })
public class GerritAmbassadorReconciler implements Reconciler<GerritNetwork> {

  @Override
  public UpdateControl<GerritNetwork> reconcile(
      GerritNetwork resource, Context<GerritNetwork> context) throws Exception {

    if (resource.getSpec().getIngress().getTls().isEnabled()) {
      // TODO: Also create a TLSContext ambassador CR
      System.out.println("TODO");
    }

    return UpdateControl.noUpdate();
  }
}
