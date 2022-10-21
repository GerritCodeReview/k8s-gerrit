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

package com.google.gerrit.k8s.operator.gerrit.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.assertj.core.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.jupiter.api.Test;

public class GerritConfigBuilderTest {

  @Test
  public void emptyGerritConfigContainsAllPresetConfiguration() {
    GerritConfigBuilder cfgBuilder = new GerritConfigBuilder();
    Config cfg = cfgBuilder.withConfig("").build();
    for (RequiredOption<?> opt : cfgBuilder.getRequiredOptions()) {
      if (opt.getExpected() instanceof String || opt.getExpected() instanceof Boolean) {
        assertTrue(
            cfg.getString(opt.getSection(), opt.getSubSection(), opt.getKey())
                .equals(opt.getExpected().toString()));
      } else if (opt.getExpected() instanceof Set) {
        assertTrue(
            Arrays.asList(cfg.getStringList(opt.getSection(), opt.getSubSection(), opt.getKey()))
                .containsAll((Set<?>) opt.getExpected()));
      }
    }
  }

  @Test
  public void invalidConfigValueIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> new GerritConfigBuilder().withConfig("[gerrit]\n  basePath = invalid").build());
  }

  @Test
  public void validConfigValueIsAccepted() {
    assertDoesNotThrow(
        () -> new GerritConfigBuilder().withConfig("[gerrit]\n  basePath = git").build());
  }

  @Test
  public void canonicalWebUrlIsConfigured() {
    String url = "https://gerrit.example.com";
    Config cfg = new GerritConfigBuilder().withConfig("").withUrl(url).build();
    assertTrue(cfg.getString("gerrit", null, "canonicalWebUrl").equals(url));
  }
}
