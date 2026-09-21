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
import com.google.gerrit.k8s.operator.api.model.maintenance.GerritMaintenance;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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
  public static final String SHARED_RSRCS = "/com/google/gerrit/k8s/operator/shared";
  public static final String SHARED_BASE = SHARED_RSRCS + "/base";
  public static final String SHARED_PATCHES = SHARED_RSRCS + "/patches";

  public static final String SHARED_BASE_GERRIT_CLUSTER = SHARED_BASE + "/gerritcluster_base.yaml";
  public static final String SHARED_BASE_GERRIT = SHARED_BASE + "/gerrit_base.yaml";
  public static final String SHARED_BASE_GERRIT_MAINTENANCE =
      SHARED_BASE + "/gerrit_maintenance_base.yaml";

  public static final String SHARED_PATCH_REPLICA_CLUSTER =
      SHARED_PATCHES + "/gerritcluster_replica.patch.yaml";
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

  /** {@link GerritMaintenance} fixture from the shared base, with optional patches. */
  public static KubernetesResourceFixture<GerritMaintenance> gerritMaintenance(
      Class<?> context, String... patches) {
    return fixture(GerritMaintenance.class, context, SHARED_BASE_GERRIT_MAINTENANCE, patches);
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
