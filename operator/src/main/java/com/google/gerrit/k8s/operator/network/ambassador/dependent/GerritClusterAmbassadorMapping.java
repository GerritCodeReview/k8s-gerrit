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

package com.google.gerrit.k8s.operator.network.ambassador.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.network.model.AmbassadorMapping;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@KubernetesDependent
public class GerritClusterAmbassadorMapping
    extends CRUDKubernetesDependentResource<GenericKubernetesResource, GerritNetwork> {
  private static final String UPLOAD_PACK_URL_PATTERN = "/.*/git-upload-pack";
  public static final String INGRESS_NAME = "gerrit-ingress";
  public static final String HOST = "";


  public GerritClusterAmbassadorMapping() {
    super(GenericKubernetesResource.class);
  }

  private Map<String, Object> buildSpec(GerritNetwork gerritNetwork, String serviceName) {
    Map<String, Object> spec = new HashMap<>();
    spec.put("ambassador_id", Arrays.asList("internal_proxy"));
    spec.put("host", HOST);
    spec.put("prefix", "/");
    spec.put("service", serviceName);
    spec.put("bypass_auth", true);
    return spec;
  }

  GenericKubernetesResource buildMapping(GerritNetwork gerritNetwork, String name) {
    GenericKubernetesResource mapping =
        (GenericKubernetesResource)
            new GenericKubernetesResourceBuilder()
                .withApiVersion("getambassador.io/v2")
                .withKind("Mapping")
                .withNewMetadata()
                .withName(name)
                .withNamespace(gerritNetwork.getMetadata().getNamespace())
                .withLabels(
                    GerritCluster.getLabels(
                        gerritNetwork.getMetadata().getName(),
                        name,
                        this.getClass().getSimpleName()))
                .endMetadata()
                .build();
    return mapping;
  }

  /*

  Case 1, 2) 0 primary 1 replica || 1 primary 0 replica

  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-ambassador-mapping
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
  name: gerrit-ambassador-mapping
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: / .* /git-upload-pack
  prefix_regex: true
  service: gerrit-replica.gerrit-operator:8080

  # Send fetch/clone GET requests to replica
  apiVersion: getambassador.io/v2
  kind: Mapping
  name: gerrit-ambassador-mapping
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
  name: gerrit-ambassador-mapping
  ambassador_id:
  - internal_proxy
  host: HOST
  prefix: /
  prefix_regex: false
  service: gerrit.gerrit-operator:8080


  */

  @Override
  protected GenericKubernetesResource desired(GerritNetwork gerritNetwork, Context<GerritNetwork> context) {

    if (gerritNetwork.getSpec().getIngress().getTls().isEnabled()) {
      // TODO: Also create a TLSContext ambassador CR
      System.out.println("TODO");
    }

    int svcPort = 8080;

    // If only one Gerrit instance in GerritCluster, send all git-over-https requests to it
    if (gerritNetwork.hasPrimaryGerrit() ^ gerritNetwork.hasGerritReplica()) {
      String svcName =
          gerritNetwork.hasPrimaryGerrit()
              ? gerritNetwork.getSpec().getPrimaryGerrit().getName() + ":" + svcPort
              : gerritNetwork.getSpec().getGerritReplica().getName() + ":" + svcPort;
      GenericKubernetesResource mapping = buildMapping(gerritNetwork, "gerrit-ambassador-mapping");
      Map<String, Object> spec = buildSpec(gerritNetwork, svcName);
      mapping.setAdditionalProperty("spec", spec);
      return mapping;
    }

    // Otherwise, send git `fetch/clone` requests to the replica and `push` requests to the primary
    // instance
    String
        primaryServiceName = gerritNetwork.getSpec().getPrimaryGerrit().getName() + ":" + svcPort,
        replicaServiceName = gerritNetwork.getSpec().getGerritReplica().getName() + ":" + svcPort;

    GenericKubernetesResource mapping1 = buildMapping(gerritNetwork, "gerrit-ambassador-mapping-1"),
        mapping2 = buildMapping(gerritNetwork, "gerrit-ambassador-mapping-2"),
        mapping3 = buildMapping(gerritNetwork, "gerrit-ambassador-mapping-3");
    Map<String, Object> spec1 = buildSpec(gerritNetwork, replicaServiceName),
        spec2 = buildSpec(gerritNetwork, replicaServiceName),
        spec3 = buildSpec(gerritNetwork, primaryServiceName);

    /**
     * git fetch/clone result in two HTTP requests to the git server: POST
     * /my-test-repo/git-upload-pack GET /my-test-repo/info/refs?service=git-upload-pack
     */
    // Send fetch/clone POST requests to replica
    spec1.put("prefix", UPLOAD_PACK_URL_PATTERN);
    spec1.put("prefix_regex", "true");
    mapping1.setAdditionalProperty("spec", spec1);

    // Send fetch/clone GET requests to replica
    spec2.put(
        "query_parameters",
        new HashMap<String, String>() {
          {
            put("service", "git-upload-pack");
          }
        });
    mapping2.setAdditionalProperty("spec", spec2);

    // Send all other requests to primary (Emissary evaluates more constrained Mappings first)
    mapping3.setAdditionalProperty("spec", spec3);

    return mapping1;
  }
}
