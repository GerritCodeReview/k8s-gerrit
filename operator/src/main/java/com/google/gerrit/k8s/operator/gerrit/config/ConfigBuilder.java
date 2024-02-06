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

package com.google.gerrit.k8s.operator.gerrit.config;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public abstract class ConfigBuilder {

  private final ImmutableList<RequiredOption<?>> requiredOptions;

  private final Config config;

  ConfigBuilder(Config baseConfig, ImmutableList<RequiredOption<?>> requiredOptions) {
    this.config = baseConfig;
    this.requiredOptions = requiredOptions;
  }

  protected ConfigBuilder(String baseConfig, ImmutableList<RequiredOption<?>> requiredOptions) {
    this.config = parseConfig(baseConfig);
    this.requiredOptions = requiredOptions;
  }

  public Config build() {
    ConfigValidator configValidator = new ConfigValidator(requiredOptions);
    try {
      configValidator.check(config);
    } catch (InvalidGerritConfigException e) {
      throw new IllegalStateException(e);
    }
    setRequiredOptions();
    return config;
  }

  public void validate() throws InvalidGerritConfigException {
    new ConfigValidator(requiredOptions).check(config);
  }

  public List<RequiredOption<?>> getRequiredOptions() {
    return this.requiredOptions;
  }

  protected Config parseConfig(String text) {
    Config cfg = new Config();
    try {
      cfg.fromText(text);
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("Invalid configuration: " + text, e);
    }
    return cfg;
  }

  @SuppressWarnings("unchecked")
  private void setRequiredOptions() {
    for (RequiredOption<?> opt : requiredOptions) {
      if (opt.getExpected() instanceof String) {
        config.setString(
            opt.getSection(), opt.getSubSection(), opt.getKey(), (String) opt.getExpected());
      } else if (opt.getExpected() instanceof Boolean) {
        config.setBoolean(
            opt.getSection(), opt.getSubSection(), opt.getKey(), (Boolean) opt.getExpected());
      } else if (opt.getExpected() instanceof Set) {
        List<String> values =
            new ArrayList<String>(
                Arrays.asList(
                    config.getStringList(opt.getSection(), opt.getSubSection(), opt.getKey())));
        List<String> expectedSet = new ArrayList<String>();
        expectedSet.addAll((Set<String>) opt.getExpected());
        expectedSet.removeAll(values);
        values.addAll(expectedSet);
        config.setStringList(opt.getSection(), opt.getSubSection(), opt.getKey(), values);
      }
    }
  }
}
