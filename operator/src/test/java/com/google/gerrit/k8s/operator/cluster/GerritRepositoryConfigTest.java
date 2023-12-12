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

package com.google.gerrit.k8s.operator.cluster;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.gerrit.k8s.operator.v1beta3.api.model.shared.GerritRepositoryConfig;
import org.junit.jupiter.api.Test;

public class GerritRepositoryConfigTest {

  @Test
  public void testFullImageNameComputesCorrectly() {
    assertThat(
        new GerritRepositoryConfig().getFullImageName("gerrit"),
        is(equalTo("docker.io/k8sgerrit/gerrit:latest")));

    GerritRepositoryConfig repoConfig1 = new GerritRepositoryConfig();
    repoConfig1.setOrg("testorg");
    repoConfig1.setRegistry("registry.example.com");
    repoConfig1.setTag("v1.0");
    assertThat(
        repoConfig1.getFullImageName("gerrit"),
        is(equalTo("registry.example.com/testorg/gerrit:v1.0")));

    GerritRepositoryConfig repoConfig2 = new GerritRepositoryConfig();
    repoConfig2.setOrg(null);
    repoConfig2.setRegistry(null);
    repoConfig2.setTag(null);
    assertThat(repoConfig2.getFullImageName("gerrit"), is(equalTo("gerrit")));
  }
}
