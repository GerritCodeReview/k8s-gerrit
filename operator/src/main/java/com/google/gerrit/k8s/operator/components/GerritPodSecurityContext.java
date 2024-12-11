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
import io.fabric8.kubernetes.api.model.Sysctl;
import java.util.ArrayList;

public class GerritPodSecurityContext extends PodSecurityContext {
  private static final long serialVersionUID = 1L;

  public GerritPodSecurityContext() {
    super(
        Constants.GERRIT_USER_GROUP_ID, // fsGroup
        null, // fsGroupChangePolicy
        Constants.GERRIT_USER_GROUP_ID, // runAsGroup
        true, // runAsNonRoot
        Constants.GERRIT_USER_ID, // runAsUser
        null, // seLinuxOptions
        null, // seccompProfile
        new ArrayList<Long>(), // supplementalGroups
        new ArrayList<Sysctl>(), // sysctls
        null // windowsOptions
        );
  }
}
