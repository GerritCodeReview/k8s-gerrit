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

import com.google.gerrit.k8s.operator.Constants.ClusterMode;
import com.google.gerrit.k8s.operator.OperatorContext;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for operator unit tests that require an {@link OperatorContext}.
 *
 * <p>{@link OperatorContext} is a singleton whose {@code createInstance} is a no-op when an
 * instance already exists. In a test suite running in a single JVM, an earlier test class may leave
 * the singleton set to a different {@link ClusterMode} than what a later class needs.
 *
 * <p>This class resets the singleton before each test method via reflection, then re-creates it
 * with the mode declared by {@link #getClusterMode()}. Subclasses only need to implement that
 * single method; no ad-hoc {@code OperatorContext.createInstance} calls are needed.
 */
public abstract class AbstractOperatorUnitTest {

  @BeforeEach
  void setUpOperatorContext() throws ReflectiveOperationException {
    Field field = OperatorContext.class.getDeclaredField("instance");
    field.setAccessible(true);
    field.set(null, null);
    OperatorContext.createInstance(getClusterMode(), getClusterDomain());
  }

  protected abstract ClusterMode getClusterMode();

  protected String getClusterDomain() {
    return "cluster.local";
  }
}
