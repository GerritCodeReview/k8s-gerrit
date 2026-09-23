// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.test;

import com.google.gerrit.k8s.operator.api.model.cluster.GerritCluster;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gitgc.GitGarbageCollection;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import com.google.gerrit.k8s.operator.api.model.network.GerritNetwork;
import com.google.gerrit.k8s.operator.api.model.receiver.Receiver;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMapping;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingGETReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPOSTReplica;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingPrimary;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiver;
import com.google.gerrit.k8s.operator.network.ambassador.dependent.GerritClusterMappingReceiverGET;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.getambassador.v2.Host;
import io.getambassador.v2.Mapping;
import io.getambassador.v2.TLSContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link KubernetesResourceFixture} instances.
 *
 * <p>Eliminates boilerplate in test {@code provideYamlManifests()} methods by centralising the
 * shared base-path prefix and providing typed factory methods for every well-known shared fixture.
 *
 * <h3>Path resolution</h3>
 *
 * <ul>
 *   <li>A path that starts with {@code /} is used verbatim as an absolute classpath resource.
 *   <li>A path that does not start with {@code /} is resolved relative to the package directory of
 *       the supplied {@code context} class, exactly as {@code Class.getResourceAsStream} does for
 *       relative names.
 * </ul>
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // Shared base fixture, no patches:
 * TestFixtures.gerritCluster(MyTest.class)
 *
 * // Shared base fixture with one shared patch:
 * TestFixtures.gerritCluster(MyTest.class,
 *     TestFixtures.SHARED_PATCH_REPLICA_CLUSTER)
 *
 * // Generic fixture resolved relative to the test class package:
 * TestFixtures.fixture(GerritNetwork.class, MyTest.class,
 *     "../../../base/gerritnetwork_base.yaml",
 *     "../../gerritnetwork_tls.patch.yaml")
 *
 * // Generic fixture with an absolute classpath path:
 * TestFixtures.fixture(Gerrit.class, MyTest.class,
 *     SHARED_BASE + "/gerrit_base.yaml")
 * }</pre>
 */
public final class TestFixtures {
  /** Pairs an ambassador mapping dependent class with its expected {@link Mapping} fixture. */
  public record ExpectedMapping(
      Class<?> dependentClass, KubernetesResourceFixture<Mapping> fixture) {}

  public static final String SHARED_RSRCS = "/com/google/gerrit/k8s/operator/shared";
  public static final String SHARED_BASE = SHARED_RSRCS + "/base";
  public static final String SHARED_PATCHES = SHARED_RSRCS + "/patches";

  public static final String SHARED_BASE_GERRIT_CLUSTER = SHARED_BASE + "/gerritcluster_base.yaml";
  public static final String SHARED_BASE_GERRIT = SHARED_BASE + "/gerrit_base.yaml";
  public static final String SHARED_BASE_GERRIT_MAINTENANCE =
      SHARED_BASE + "/gerrit_maintenance_base.yaml";

  public static final String SHARED_PATCH_REPLICA_CLUSTER =
      SHARED_PATCHES + "/gerritcluster_replica.patch.yaml";
  public static final String SHARED_PATCH_MAINTENANCE_CLUSTER =
      SHARED_PATCHES + "/gerritcluster_maintenance.patch.yaml";
  public static final String GERRIT_CLUSTER_NFS_PATCH =
      SHARED_PATCHES + "/gerritcluster_nfs_workaround.patch.yaml";
  public static final String SHARED_PATCH_REPLICA_GERRIT =
      SHARED_PATCHES + "/gerrit_replica.patch.yaml";
  public static final String SHARED_PATCH_PRIMARY_WITH_REPLICA_GERRIT =
      SHARED_PATCHES + "/gerrit_primary_with_replica.patch.yaml";
  public static final String SHARED_PATCH_HA_GERRIT = SHARED_PATCHES + "/gerrit_ha.patch.yaml";
  public static final String SHARED_PATCH_ES_GERRIT = SHARED_PATCHES + "/gerrit_es.patch.yaml";
  public static final String SHARED_PATCH_OS_GERRIT = SHARED_PATCHES + "/gerrit_os.patch.yaml";
  public static final String SHARED_PATCH_INGRESS_HTTP_GERRIT =
      SHARED_PATCHES + "/gerrit_ingress_http.patch.yaml";
  public static final String SHARED_PATCH_INGRESS_HTTPS_GERRIT =
      SHARED_PATCHES + "/gerrit_ingress_https.patch.yaml";

  private static final String GERRIT_DEPENDENT = "/com/google/gerrit/k8s/operator/gerrit/dependent";

  public static final String GERRIT_CONFIGMAP_BASE =
      GERRIT_DEPENDENT + "/gerrit_configmap_base.yaml";
  public static final String GERRIT_CONFIGMAP_HA_PATCH =
      GERRIT_DEPENDENT + "/gerrit_configmap_ha.patch.yaml";
  public static final String GERRIT_CONFIGMAP_ES_PATCH =
      GERRIT_DEPENDENT + "/gerrit_configmap_es.patch.yaml";
  public static final String GERRIT_CONFIGMAP_OS_PATCH =
      GERRIT_DEPENDENT + "/gerrit_configmap_os.patch.yaml";
  public static final String GERRIT_CONFIGMAP_INGRESS_HTTP_PATCH =
      GERRIT_DEPENDENT + "/gerrit_configmap_ingress_http.patch.yaml";
  public static final String GERRIT_CONFIGMAP_INGRESS_HTTPS_PATCH =
      GERRIT_DEPENDENT + "/gerrit_configmap_ingress_https.patch.yaml";

  public static final String GERRIT_INIT_CONFIGMAP_BASE =
      GERRIT_DEPENDENT + "/gerrit-init_configmap_base.yaml";
  public static final String GERRIT_INIT_CONFIGMAP_HA_PATCH =
      GERRIT_DEPENDENT + "/gerrit-init_configmap_ha.patch.yaml";

  public static final String GERRIT_STATEFULSET_BASE =
      GERRIT_DEPENDENT + "/gerrit_statefulset_base.yaml";
  public static final String GERRIT_STATEFULSET_HA_PATCH =
      GERRIT_DEPENDENT + "/gerrit_statefulset_ha.patch.yaml";
  public static final String GERRIT_STATEFULSET_INDEX_PATCH =
      GERRIT_DEPENDENT + "/gerrit_statefulset_index.patch.yaml";

  public static final String GERRIT_SERVICE_BASE = GERRIT_DEPENDENT + "/gerrit_service_base.yaml";
  public static final String GERRIT_SERVICE_HA_PATCH =
      GERRIT_DEPENDENT + "/gerrit_service_ha.patch.yaml";

  public static final String GERRIT_HEADLESS_SERVICE_BASE =
      GERRIT_DEPENDENT + "/gerrit_headless_service_base.yaml";

  public static final String MAINTENANCE_GC_SELECTED_PATCH =
      SHARED_PATCHES + "/gerrit_maintenance_gc_selected.patch.yaml";
  public static final String MAINTENANCE_GC_SELECTED_OPTIONS_PATCH =
      SHARED_PATCHES + "/gerrit_maintenance_gc_selected_options.patch.yaml";

  public static final String MAINTENANCE_CRONJOB_BASE = SHARED_BASE + "/cronjob_gc_all_base.yaml";
  public static final String MAINTENANCE_CRONJOB_GC_SELECTED_PATCH =
      SHARED_PATCHES + "/cronjob_gc_selected.patch.yaml";
  public static final String MAINTENANCE_CRONJOB_GC_SELECTED_OPTIONS_PATCH =
      SHARED_PATCHES + "/cronjob_gc_selected_options.patch.yaml";

  private static final String GITGC_RSRCS = "/com/google/gerrit/k8s/operator/gitgc";
  private static final String GITGC_DEPENDENT = GITGC_RSRCS + "/dependent";

  public static final String GITGC_BASE = GITGC_RSRCS + "/gitgc_base.yaml";
  public static final String GITGC_SELECTED_PATCH = GITGC_RSRCS + "/gitgc_selected.patch.yaml";
  public static final String GITGC_OPTIONS_PATCH = GITGC_RSRCS + "/gitgc_options.patch.yaml";

  public static final String GITGC_CRONJOB_BASE = GITGC_DEPENDENT + "/cronjob_base.yaml";
  public static final String GITGC_CRONJOB_ARGS_SELECTED_PATCH =
      GITGC_DEPENDENT + "/cronjob_args_selected.patch.yaml";
  public static final String GITGC_CRONJOB_ARGS_OPTIONS_PATCH =
      GITGC_DEPENDENT + "/cronjob_args_options.patch.yaml";
  public static final String GITGC_CRONJOB_ARGS_OPTIONS_APPEND_PATCH =
      GITGC_DEPENDENT + "/cronjob_args_options_append.patch.yaml";
  public static final String GITGC_CRONJOB_NFS_PATCH =
      GITGC_DEPENDENT + "/cronjob_nfs_workaround.patch.yaml";

  private static final String INDEXER_RSRCS = "/com/google/gerrit/k8s/operator/indexer";
  private static final String INDEXER_DEPENDENT = INDEXER_RSRCS + "/dependent";

  public static final String INDEXER_BASE = INDEXER_RSRCS + "/gerrit_indexer_base.yaml";
  public static final String INDEXER_OS_PATCH = INDEXER_RSRCS + "/gerrit_indexer_os.patch.yaml";

  public static final String INDEXER_JOB_BASE = INDEXER_DEPENDENT + "/gerrit_indexer_job_base.yaml";
  public static final String INDEXER_JOB_ES_PATCH =
      INDEXER_DEPENDENT + "/gerrit_indexer_job_es.patch.yaml";
  public static final String INDEXER_JOB_OS_PATCH =
      INDEXER_DEPENDENT + "/gerrit_indexer_job_os.patch.yaml";

  public static final String INDEXER_CONFIGMAP_BASE =
      INDEXER_DEPENDENT + "/gerrit_indexer_configmap_base.yaml";
  public static final String INDEXER_CONFIGMAP_ES_PATCH =
      INDEXER_DEPENDENT + "/gerrit_indexer_configmap_es.patch.yaml";
  public static final String INDEXER_CONFIGMAP_OS_PATCH =
      INDEXER_DEPENDENT + "/gerrit_indexer_configmap_os.patch.yaml";

  private static final String NETWORK_RSRCS = "/com/google/gerrit/k8s/operator/network";
  private static final String AMBASSADOR_DEPENDENT = NETWORK_RSRCS + "/ambassador/dependent";

  public static final String GERRITNETWORK_BASE = NETWORK_RSRCS + "/gerritnetwork_base.yaml";
  public static final String GERRITNETWORK_AMBASSADOR_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_ambassador.patch.yaml";
  public static final String GERRITNETWORK_TLS_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_tls.patch.yaml";
  public static final String GERRITNETWORK_CREATE_HOST_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_create_host.patch.yaml";
  public static final String GERRITNETWORK_PRIMARY_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_primary.patch.yaml";
  public static final String GERRITNETWORK_REPLICA_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_replica.patch.yaml";
  public static final String GERRITNETWORK_RECEIVER_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_receiver.patch.yaml";
  public static final String GERRITNETWORK_ISTIO_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_istio.patch.yaml";
  public static final String GERRITNETWORK_SSH_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_ssh.patch.yaml";
  public static final String GERRITNETWORK_REPLICA_SSH_PORT_PATCH =
      NETWORK_RSRCS + "/gerritnetwork_replica_ssh_port.patch.yaml";

  private static final String ISTIO_DEPENDENT = NETWORK_RSRCS + "/istio/dependent";

  public static final String ISTIO_GATEWAY_BASE = ISTIO_DEPENDENT + "/gateway_base.yaml";
  public static final String ISTIO_GATEWAY_TLS_PATCH = ISTIO_DEPENDENT + "/gateway_tls.patch.yaml";
  public static final String ISTIO_GATEWAY_SSH_PRIMARY_PATCH =
      ISTIO_DEPENDENT + "/gateway_ssh_primary.patch.yaml";
  public static final String ISTIO_GATEWAY_SSH_REPLICA_PATCH =
      ISTIO_DEPENDENT + "/gateway_ssh_replica.patch.yaml";

  public static final String ISTIO_VIRTUALSERVICE_BASE =
      ISTIO_DEPENDENT + "/virtualservice_base.yaml";
  public static final String ISTIO_VIRTUALSERVICE_RECEIVER_ROUTE_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_receiver_route.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_RECEIVER_MATCH_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_receiver_match.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_RECEIVER_REPLICA_HTTP_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_add_replica_route.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_REPLICA_READ_MATCH_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_replica_read_match.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_PRIMARY_HTTP_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_primary_http.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_REPLICA_HTTP_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_replica_http.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_TCP_PRIMARY_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_tcp_primary.patch.yaml";
  public static final String ISTIO_VIRTUALSERVICE_TCP_REPLICA_PATCH =
      ISTIO_DEPENDENT + "/virtualservice_tcp_replica.patch.yaml";

  public static final String ISTIO_DESTINATIONRULE_PRIMARY =
      ISTIO_DEPENDENT + "/destinationrule_primary.yaml";
  public static final String ISTIO_DESTINATIONRULE_PRIMARY_HEADLESS =
      ISTIO_DEPENDENT + "/destinationrule_primary_headless.yaml";
  public static final String ISTIO_DESTINATIONRULE_PRIMARY_HEADLESS_PODS =
      ISTIO_DEPENDENT + "/destinationrule_primary_headless_pods.yaml";
  public static final String ISTIO_DESTINATIONRULE_REPLICA =
      ISTIO_DEPENDENT + "/destinationrule_replica.yaml";
  public static final String ISTIO_DESTINATIONRULE_REPLICA_HEADLESS =
      ISTIO_DEPENDENT + "/destinationrule_replica_headless.yaml";
  public static final String ISTIO_DESTINATIONRULE_REPLICA_HEADLESS_PODS =
      ISTIO_DEPENDENT + "/destinationrule_replica_headless_pods.yaml";

  private static final String INGRESS_DEPENDENT = NETWORK_RSRCS + "/ingress/dependent";

  public static final String INGRESS_BASE = INGRESS_DEPENDENT + "/ingress_base.yaml";
  public static final String INGRESS_TLS_PATCH = INGRESS_DEPENDENT + "/ingress_tls.patch.yaml";
  public static final String INGRESS_SNIPPET_PRIMARY_PATCH =
      INGRESS_DEPENDENT + "/ingress_snippet_primary.patch.yaml";
  public static final String INGRESS_SNIPPET_PRIMARY_REPLICA_PATCH =
      INGRESS_DEPENDENT + "/ingress_snippet_primary_replica.patch.yaml";
  public static final String INGRESS_SNIPPET_RECEIVER_PATCH =
      INGRESS_DEPENDENT + "/ingress_snippet_receiver.patch.yaml";
  public static final String INGRESS_PATHS_RECEIVER_PATCH =
      INGRESS_DEPENDENT + "/ingress_paths_receiver.patch.yaml";
  public static final String INGRESS_PATHS_REPLICA_PATCH =
      INGRESS_DEPENDENT + "/ingress_paths_replica.patch.yaml";
  public static final String INGRESS_PATHS_PRIMARY_PATCH =
      INGRESS_DEPENDENT + "/ingress_paths_primary.patch.yaml";
  public static final String INGRESS_PATHS_PRIMARY_REPLICA_PATCH =
      INGRESS_DEPENDENT + "/ingress_paths_primary_replica.patch.yaml";
  public static final String INGRESS_PATHS_RECEIVER_WRITE_PATCH =
      INGRESS_DEPENDENT + "/ingress_paths_receiver_write.patch.yaml";

  private static final String RECEIVER_RSRCS = "/com/google/gerrit/k8s/operator/receiver";
  private static final String RECEIVER_DEPENDENT = RECEIVER_RSRCS + "/dependent";

  public static final String RECEIVER_BASE = RECEIVER_RSRCS + "/receiver_base.yaml";
  public static final String RECEIVER_OPTIONS_PATCH =
      RECEIVER_RSRCS + "/receiver_options.patch.yaml";

  public static final String RECEIVER_DEPLOYMENT_BASE =
      RECEIVER_DEPENDENT + "/deployment_base.yaml";
  public static final String RECEIVER_DEPLOYMENT_OPTIONS_PATCH =
      RECEIVER_DEPENDENT + "/deployment_options.patch.yaml";
  public static final String RECEIVER_SERVICE_BASE = RECEIVER_DEPENDENT + "/service.yaml";

  public static final String AMBASSADOR_MAPPING_BASE = AMBASSADOR_DEPENDENT + "/mapping_base.yaml";

  public static final String AMBASSADOR_MAPPING_NAME_PRIMARY_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_primary.patch.yaml";
  public static final String AMBASSADOR_MAPPING_NAME_DEFAULT_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_default.patch.yaml";
  public static final String AMBASSADOR_MAPPING_NAME_GET_REPLICA_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_get_replica.patch.yaml";
  public static final String AMBASSADOR_MAPPING_NAME_POST_REPLICA_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_post_replica.patch.yaml";
  public static final String AMBASSADOR_MAPPING_NAME_RECEIVER_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_receiver.patch.yaml";
  public static final String AMBASSADOR_MAPPING_NAME_GET_RECEIVER_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_name_get_receiver.patch.yaml";
  public static final String AMBASSADOR_MAPPING_SERVICE_PRIMARY_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_service_primary.patch.yaml";
  public static final String AMBASSADOR_MAPPING_SERVICE_REPLICA_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_service_replica.patch.yaml";
  public static final String AMBASSADOR_MAPPING_SERVICE_RECEIVER_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_service_receiver.patch.yaml";
  public static final String AMBASSADOR_MAPPING_GET_REPLICA_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_get_replica.patch.yaml";
  public static final String AMBASSADOR_MAPPING_POST_REPLICA_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_post_replica.patch.yaml";
  public static final String AMBASSADOR_MAPPING_RECEIVER_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_receiver.patch.yaml";
  public static final String AMBASSADOR_MAPPING_RECEIVER_ONLY_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_receiver_only.patch.yaml";
  public static final String AMBASSADOR_MAPPING_GET_RECEIVER_PATCH =
      AMBASSADOR_DEPENDENT + "/mapping_get_receiver.patch.yaml";
  public static final String AMBASSADOR_TLSCONTEXT_BASE =
      AMBASSADOR_DEPENDENT + "/tlscontext_base.yaml";
  public static final String AMBASSADOR_HOST_BASE = AMBASSADOR_DEPENDENT + "/host_base.yaml";
  public static final String AMBASSADOR_HOST_TLS_PATCH =
      AMBASSADOR_DEPENDENT + "/host_tls.patch.yaml";

  /** {@link GerritCluster} fixture from the shared base, with optional shared patches. */
  public static KubernetesResourceFixture<GerritCluster> gerritCluster(
      Class<?> context, String... patches) {
    return fixture(GerritCluster.class, context, SHARED_BASE_GERRIT_CLUSTER, patches);
  }

  /** {@link Gerrit} fixture from the shared base, with optional patches. */
  public static KubernetesResourceFixture<Gerrit> gerrit(Class<?> context, String... patches) {
    return fixture(Gerrit.class, context, SHARED_BASE_GERRIT, patches);
  }

  /** {@link ConfigMap} fixture for the Gerrit configmap, with optional patches. */
  public static KubernetesResourceFixture<ConfigMap> gerritConfigMap(String... patches) {
    return fixture(ConfigMap.class, TestFixtures.class, GERRIT_CONFIGMAP_BASE, patches);
  }

  /** {@link ConfigMap} fixture for the Gerrit init configmap, with optional patches. */
  public static KubernetesResourceFixture<ConfigMap> gerritInitConfigMap(String... patches) {
    return fixture(ConfigMap.class, TestFixtures.class, GERRIT_INIT_CONFIGMAP_BASE, patches);
  }

  /** {@link StatefulSet} fixture for the Gerrit StatefulSet, with optional patches. */
  public static KubernetesResourceFixture<StatefulSet> gerritStatefulSet(String... patches) {
    return fixture(StatefulSet.class, TestFixtures.class, GERRIT_STATEFULSET_BASE, patches);
  }

  /** {@link Service} fixture for the Gerrit service, with optional patches. */
  public static KubernetesResourceFixture<Service> gerritService(String... patches) {
    return fixture(Service.class, TestFixtures.class, GERRIT_SERVICE_BASE, patches);
  }

  /** {@link Service} fixture for the Gerrit headless service, with optional patches. */
  public static KubernetesResourceFixture<Service> gerritHeadlessService(String... patches) {
    return fixture(Service.class, TestFixtures.class, GERRIT_HEADLESS_SERVICE_BASE, patches);
  }

  /** {@link GitGarbageCollection} fixture from the base, with optional patches. */
  public static KubernetesResourceFixture<GitGarbageCollection> gitGarbageCollection(
      String... patches) {
    return fixture(GitGarbageCollection.class, TestFixtures.class, GITGC_BASE, patches);
  }

  /** {@link CronJob} fixture for GitGc tests from the base, with optional patches. */
  public static KubernetesResourceFixture<CronJob> gitGcCronJob(String... patches) {
    return fixture(CronJob.class, TestFixtures.class, GITGC_CRONJOB_BASE, patches);
  }

  /** {@link GerritIndexer} fixture from the base, with optional patches. */
  public static KubernetesResourceFixture<GerritIndexer> gerritIndexer(String... patches) {
    return fixture(GerritIndexer.class, TestFixtures.class, INDEXER_BASE, patches);
  }

  /** {@link Job} fixture for indexer tests from the base, with optional patches. */
  public static KubernetesResourceFixture<Job> indexerJob(String... patches) {
    return fixture(Job.class, TestFixtures.class, INDEXER_JOB_BASE, patches);
  }

  /** {@link ConfigMap} fixture for indexer tests from the base, with optional patches. */
  public static KubernetesResourceFixture<ConfigMap> indexerConfigMap(String... patches) {
    return fixture(ConfigMap.class, TestFixtures.class, INDEXER_CONFIGMAP_BASE, patches);
  }

  /** {@link GerritMaintenance} fixture from the shared base, with optional patches. */
  public static KubernetesResourceFixture<GerritMaintenance> gerritMaintenance(
      Class<?> context, String... patches) {
    return fixture(GerritMaintenance.class, context, SHARED_BASE_GERRIT_MAINTENANCE, patches);
  }

  /** {@link CronJob} fixture for a GerritMaintenance CronJob, with optional patches. */
  public static KubernetesResourceFixture<CronJob> maintenanceCronJob(String... patches) {
    return fixture(CronJob.class, TestFixtures.class, MAINTENANCE_CRONJOB_BASE, patches);
  }

  /** {@link GerritNetwork} fixture from the base, with optional patches. */
  public static KubernetesResourceFixture<GerritNetwork> gerritNetwork(String... patches) {
    return fixture(GerritNetwork.class, TestFixtures.class, GERRITNETWORK_BASE, patches);
  }

  /** {@link ExpectedMapping} for the primary Gerrit in a primary+replica topology. */
  public static ExpectedMapping ambassadorMappingPrimary() {
    return new ExpectedMapping(
        GerritClusterMappingPrimary.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_PRIMARY_PATCH, AMBASSADOR_MAPPING_SERVICE_PRIMARY_PATCH));
  }

  /** {@link ExpectedMapping} for the default (single-service) mapping routed to primary. */
  public static ExpectedMapping ambassadorMappingDefaultPrimary() {
    return new ExpectedMapping(
        GerritClusterMapping.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_DEFAULT_PATCH, AMBASSADOR_MAPPING_SERVICE_PRIMARY_PATCH));
  }

  /** {@link ExpectedMapping} for the default (single-service) mapping routed to replica. */
  public static ExpectedMapping ambassadorMappingDefaultReplica() {
    return new ExpectedMapping(
        GerritClusterMapping.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_DEFAULT_PATCH, AMBASSADOR_MAPPING_SERVICE_REPLICA_PATCH));
  }

  /** {@link ExpectedMapping} for GET replica read traffic. */
  public static ExpectedMapping ambassadorMappingGetReplica() {
    return new ExpectedMapping(
        GerritClusterMappingGETReplica.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_GET_REPLICA_PATCH,
            AMBASSADOR_MAPPING_SERVICE_REPLICA_PATCH,
            AMBASSADOR_MAPPING_GET_REPLICA_PATCH));
  }

  /** {@link ExpectedMapping} for POST replica write traffic. */
  public static ExpectedMapping ambassadorMappingPostReplica() {
    return new ExpectedMapping(
        GerritClusterMappingPOSTReplica.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_POST_REPLICA_PATCH,
            AMBASSADOR_MAPPING_SERVICE_REPLICA_PATCH,
            AMBASSADOR_MAPPING_POST_REPLICA_PATCH));
  }

  /** {@link ExpectedMapping} for the receiver in a replica+receiver topology. */
  public static ExpectedMapping ambassadorMappingReceiver() {
    return new ExpectedMapping(
        GerritClusterMappingReceiver.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_SERVICE_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_RECEIVER_PATCH));
  }

  /** {@link ExpectedMapping} for the receiver in a receiver-only topology. */
  public static ExpectedMapping ambassadorMappingReceiverOnly() {
    return new ExpectedMapping(
        GerritClusterMappingReceiver.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_SERVICE_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_RECEIVER_ONLY_PATCH));
  }

  /** {@link ExpectedMapping} for the receiver GET mapping. */
  public static ExpectedMapping ambassadorMappingGetReceiver() {
    return new ExpectedMapping(
        GerritClusterMappingReceiverGET.class,
        ambassadorMapping(
            AMBASSADOR_MAPPING_NAME_GET_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_SERVICE_RECEIVER_PATCH,
            AMBASSADOR_MAPPING_GET_RECEIVER_PATCH));
  }

  /** {@link Mapping} fixture from the ambassador mapping base, with optional patches. */
  public static KubernetesResourceFixture<Mapping> ambassadorMapping(String... patches) {
    return fixture(Mapping.class, TestFixtures.class, AMBASSADOR_MAPPING_BASE, patches);
  }

  /** {@link TLSContext} fixture for the ambassador TLS context (no patches needed). */
  public static KubernetesResourceFixture<TLSContext> ambassadorTlsContext() {
    return fixture(TLSContext.class, TestFixtures.class, AMBASSADOR_TLSCONTEXT_BASE);
  }

  /** {@link Host} fixture from the ambassador host base, with optional patches. */
  public static KubernetesResourceFixture<Host> ambassadorHost(String... patches) {
    return fixture(Host.class, TestFixtures.class, AMBASSADOR_HOST_BASE, patches);
  }

  /** {@link Gateway} fixture from the istio gateway base, with optional patches. */
  public static KubernetesResourceFixture<Gateway> istioGateway(String... patches) {
    return fixture(Gateway.class, TestFixtures.class, ISTIO_GATEWAY_BASE, patches);
  }

  /** {@link VirtualService} fixture from the istio virtualservice base, with optional patches. */
  public static KubernetesResourceFixture<VirtualService> istioVirtualService(String... patches) {
    return fixture(VirtualService.class, TestFixtures.class, ISTIO_VIRTUALSERVICE_BASE, patches);
  }

  /** {@link DestinationRule} fixture loaded directly from its absolute path (no base+patch). */
  public static KubernetesResourceFixture<DestinationRule> istioDestinationRule(String path) {
    return fixture(DestinationRule.class, TestFixtures.class, path);
  }

  /** The three primary {@link DestinationRule} fixtures. */
  public static List<KubernetesResourceFixture<DestinationRule>> istioDestinationRulesPrimary() {
    return List.of(
        istioDestinationRule(ISTIO_DESTINATIONRULE_PRIMARY),
        istioDestinationRule(ISTIO_DESTINATIONRULE_PRIMARY_HEADLESS),
        istioDestinationRule(ISTIO_DESTINATIONRULE_PRIMARY_HEADLESS_PODS));
  }

  /** The three replica {@link DestinationRule} fixtures. */
  public static List<KubernetesResourceFixture<DestinationRule>> istioDestinationRulesReplica() {
    return List.of(
        istioDestinationRule(ISTIO_DESTINATIONRULE_REPLICA),
        istioDestinationRule(ISTIO_DESTINATIONRULE_REPLICA_HEADLESS),
        istioDestinationRule(ISTIO_DESTINATIONRULE_REPLICA_HEADLESS_PODS));
  }

  /** {@link Ingress} fixture from the nginx ingress base, with optional patches. */
  public static KubernetesResourceFixture<Ingress> nginxIngress(String... patches) {
    return fixture(Ingress.class, TestFixtures.class, INGRESS_BASE, patches);
  }

  /** {@link Receiver} fixture from the receiver base, with optional patches. */
  public static KubernetesResourceFixture<Receiver> receiver(String... patches) {
    return fixture(Receiver.class, TestFixtures.class, RECEIVER_BASE, patches);
  }

  /** {@link Deployment} fixture for the receiver deployment base, with optional patches. */
  public static KubernetesResourceFixture<Deployment> receiverDeployment(String... patches) {
    return fixture(Deployment.class, TestFixtures.class, RECEIVER_DEPLOYMENT_BASE, patches);
  }

  /** {@link Service} fixture for the receiver service (shared across all test cases). */
  public static KubernetesResourceFixture<Service> receiverService() {
    return fixture(Service.class, TestFixtures.class, RECEIVER_SERVICE_BASE);
  }

  /**
   * Creates a {@link KubernetesResourceFixture} for any {@link KubernetesResource} type.
   *
   * <p>The {@code baseFile} and each entry in {@code patches} are resolved as follows:
   *
   * <ul>
   *   <li>Paths starting with {@code /} are absolute classpath resources used verbatim.
   *   <li>All other paths are relative to the package directory of {@code context}, consistent with
   *       {@link Class#getResourceAsStream(String)}.
   * </ul>
   *
   * @param type the target resource type
   * @param context the test class used to resolve relative resource paths
   * @param baseFile base YAML file (absolute or relative)
   * @param patches zero or more patch files (absolute or relative), applied in order
   */
  public static <T extends KubernetesResource> KubernetesResourceFixture<T> fixture(
      Class<T> type, Class<?> context, String baseFile, String... patches) {
    String resolvedBase = resolve(context, baseFile);
    List<String> resolvedPatches = new ArrayList<>();
    for (String patch : patches) {
      resolvedPatches.add(resolve(context, patch));
    }
    return new KubernetesResourceFixture<>(type, context, resolvedBase, resolvedPatches);
  }

  /**
   * Converts a relative path to the absolute classpath form expected by {@link
   * KubernetesResourceFixture}.
   *
   * <p>Absolute paths (starting with {@code /}) are returned as-is. Relative paths are prefixed
   * with the package directory of {@code context}, e.g. for a context class in package {@code
   * com.example.foo} the path {@code bar.yaml} becomes {@code /com/example/foo/bar.yaml}.
   */
  public static String resolve(Class<?> context, String path) {
    if (path.startsWith("/")) {
      return path;
    }
    String packagePath = context.getPackageName().replace('.', '/');
    return "/" + packagePath + "/" + path;
  }
}
