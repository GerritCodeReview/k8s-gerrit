// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.k8s.operator.components;

import com.google.gerrit.k8s.operator.Constants;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.SeccompProfile;
import io.fabric8.kubernetes.api.model.SeccompProfileBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;

public class GerritSecurityContext {
  private static final SeccompProfile DEFAULT_SEC_COMP_PROFILE =
      new SeccompProfileBuilder().withType("RuntimeDefault").build();
  ;

  public static PodSecurityContext forPod() {
    return new PodSecurityContextBuilder()
        .withFsGroup(Constants.GERRIT_USER_GROUP_ID)
        .withRunAsGroup(Constants.GERRIT_USER_GROUP_ID)
        .withRunAsNonRoot(true)
        .withRunAsUser(Constants.GERRIT_USER_ID)
        .withSeccompProfile(DEFAULT_SEC_COMP_PROFILE)
        .build();
  }

  public static SecurityContext forContainer() {
    return new SecurityContextBuilder()
        .withRunAsGroup(Constants.GERRIT_USER_GROUP_ID)
        .withRunAsNonRoot(true)
        .withRunAsUser(Constants.GERRIT_USER_ID)
        .withReadOnlyRootFilesystem(true)
        .withAllowPrivilegeEscalation(false)
        .withSeccompProfile(DEFAULT_SEC_COMP_PROFILE)
        .withNewCapabilities()
        .addToDrop("ALL")
        .endCapabilities()
        .build();
  }
}
