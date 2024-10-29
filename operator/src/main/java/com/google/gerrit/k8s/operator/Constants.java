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

package com.google.gerrit.k8s.operator;

import com.google.inject.AbstractModule;

public class Constants extends AbstractModule {
  public static final String VERSION = "v1beta13";

  // The resource kind always has to be plural for use in webhooks
  public static final String GERRIT_CLUSTER_KIND = "gerritclusters";
  public static final String GERRIT_KIND = "gerrits";
  public static final String GIT_GC_KIND = "gitgarbagecollections";

  public static final String[] RESOURCES_WITH_VALIDATING_WEBHOOK =
      new String[] {GERRIT_CLUSTER_KIND, GERRIT_KIND, GIT_GC_KIND};

  public static final long GERRIT_USER_GROUP_ID = 100L;

  public enum ClusterMode {
    HIGH_AVAILABILITY,
    MULTISITE
  }
}
