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

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A self-contained mock operator installation for unit tests.
 *
 * <p>Wraps a Fabric8 {@link KubernetesServer} running in CRUD mode and a real JOSDK {@link
 * Operator}. Tests create primary custom resources via {@link #create}, and the operator's
 * reconciliation loop creates the secondary resources. {@link #awaitResource} blocks until a
 * secondary resource appears in the mock store, after which it can be compared against fixtures.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * try (MockOperatorInstallation mock = new MockOperatorInstallation(
 *         client -> new GerritReconciler(client))) {
 *   mock.create(gerrit);
 *   ConfigMap cm = mock.awaitResource(
 *       ConfigMap.class, "gerrit", "gerrit-configmap", Duration.ofSeconds(30));
 *   assertConfigMapEqual(cm, expectedCm);
 * }
 * }</pre>
 */
public final class MockOperatorInstallation implements AutoCloseable {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private static final GenericRetry FAST_RETRY =
      new GenericRetry().setInitialInterval(100).setIntervalMultiplier(1).setMaxAttempts(20);

  private final KubernetesServer server;
  private final Operator operator;

  /**
   * Creates and starts the mock operator installation.
   *
   * @param reconcilerFactories one or more factories that accept the mock {@link KubernetesClient}
   *     and return a configured {@link Reconciler}; use {@code client -> new MyReconciler()} for
   *     reconcilers that take no client
   */
  @SafeVarargs
  public MockOperatorInstallation(
      Function<KubernetesClient, Reconciler<?>>... reconcilerFactories) {
    Logger.getLogger("okhttp3.mockwebserver").setLevel(Level.WARNING);

    server = new KubernetesServer(true, true);
    server.before();

    KubernetesClient client = server.getClient();
    operator =
        new Operator(
            overrider ->
                overrider
                    .withSSABasedCreateUpdateMatchForDependentResources(false)
                    .withKubernetesClient(client));

    for (Function<KubernetesClient, Reconciler<?>> factory : reconcilerFactories) {
      operator.register(factory.apply(client), overrider -> overrider.withRetry(FAST_RETRY));
    }
    operator.start();
  }

  /** Returns the {@link KubernetesClient} backed by the CRUD mock server. */
  public KubernetesClient getClient() {
    return server.getClient();
  }

  /**
   * Creates a resource in the mock Kubernetes store, triggering reconciliation.
   *
   * @param resource the primary resource to create
   * @return the resource as stored by the mock server
   */
  public <T extends HasMetadata> T create(T resource) {
    return server.getClient().resource(resource).create();
  }

  /**
   * Waits up to {@code timeout} for a resource to appear in the mock store, then returns it.
   *
   * @throws org.awaitility.core.ConditionTimeoutException if the resource does not appear in time
   */
  public <T extends HasMetadata> T awaitResource(
      Class<T> type, String namespace, String name, Duration timeout) {
    return await()
        .atMost(timeout)
        .until(
            () -> server.getClient().resources(type).inNamespace(namespace).withName(name).get(),
            Objects::nonNull);
  }

  /**
   * Waits up to 30 seconds for a resource to appear, then returns it.
   *
   * @throws org.awaitility.core.ConditionTimeoutException if the resource does not appear in time
   */
  public <T extends HasMetadata> T awaitResource(Class<T> type, String namespace, String name) {
    return awaitResource(type, namespace, name, DEFAULT_TIMEOUT);
  }

  /**
   * Waits up to {@code timeout} for a cluster-scoped resource to appear in the mock store, then
   * returns it.
   *
   * @throws org.awaitility.core.ConditionTimeoutException if the resource does not appear in time
   */
  public <T extends HasMetadata> T awaitClusterResource(
      Class<T> type, String name, Duration timeout) {
    return await()
        .atMost(timeout)
        .until(() -> server.getClient().resources(type).withName(name).get(), Objects::nonNull);
  }

  /**
   * Waits up to 30 seconds for a cluster-scoped resource to appear, then returns it.
   *
   * @throws org.awaitility.core.ConditionTimeoutException if the resource does not appear in time
   */
  public <T extends HasMetadata> T awaitClusterResource(Class<T> type, String name) {
    return awaitClusterResource(type, name, DEFAULT_TIMEOUT);
  }

  /**
   * Asserts that a namespaced resource does NOT appear within the default timeout.
   *
   * <p>Waits briefly for the reconciler to run, then asserts the resource is absent.
   */
  public <T extends HasMetadata> void awaitAbsent(Class<T> type, String namespace, String name) {
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(DEFAULT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        server
                            .getClient()
                            .resources(type)
                            .inNamespace(namespace)
                            .withName(name)
                            .get())
                    .isNull());
  }

  /**
   * Asserts that a cluster-scoped resource does NOT appear within the default timeout.
   *
   * <p>Waits briefly for the reconciler to run, then asserts the resource is absent.
   */
  public <T extends HasMetadata> void awaitAbsent(Class<T> type, String name) {
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(DEFAULT_TIMEOUT)
        .untilAsserted(
            () -> assertThat(server.getClient().resources(type).withName(name).get()).isNull());
  }

  @Override
  public void close() {
    operator.stop();
    server.after();
  }
}
