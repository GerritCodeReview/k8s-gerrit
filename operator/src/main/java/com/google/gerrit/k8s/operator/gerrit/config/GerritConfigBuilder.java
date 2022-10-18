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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@SuppressWarnings("rawtypes")
public class GerritConfigBuilder {
  private static final Set<RequiredOption> requiredOptions = setupRequiredOptions();

  public static Set<RequiredOption> setupRequiredOptions() {
    Set<RequiredOption> requiredOptions = new HashSet<>();
    requiredOptions.add(
        new RequiredOption<String>("container", "javaHome", "/usr/lib/jvm/java-11-openjdk"));
    requiredOptions.add(
        new RequiredOption<Set<String>>(
            "container",
            "javaOptions",
            Set.of("-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore")));
    requiredOptions.add(new RequiredOption<String>("container", "user", "gerrit"));
    requiredOptions.add(new RequiredOption<String>("gerrit", "basepath", "git"));
    requiredOptions.add(new RequiredOption<Boolean>("index", "onlineUpgrade", false));
    return requiredOptions;
  }

  public static Config builFromText(String text) {
    Config cfg = new Config();
    try {
      cfg.fromText(text);
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("The provided gerrit.config is invalid.");
    }

    return buildFromConfig(cfg);
  }

  public static Config buildFromConfig(Config cfg) {
    GerritConfigValidator configValidator = new GerritConfigValidator(requiredOptions);
    configValidator.check(cfg);

    return setRequiredOptions(cfg);
  }

  @SuppressWarnings("unchecked")
  private static Config setRequiredOptions(Config cfg) {
    for (RequiredOption<?> opt : requiredOptions) {
      if (opt.getExpected() instanceof String) {
        cfg.setString(
            opt.getSection(), opt.getSubSection(), opt.getKey(), (String) opt.getExpected());
      } else if (opt.getExpected() instanceof Boolean) {
        cfg.setBoolean(
            opt.getSection(), opt.getSubSection(), opt.getKey(), (Boolean) opt.getExpected());
      } else if (opt.getExpected() instanceof Set) {
        List<String> values =
            new ArrayList<String>(
                Arrays.asList(
                    cfg.getStringList(opt.getSection(), opt.getSubSection(), opt.getKey())));
        Set<String> expectedSet = new HashSet<String>();
        expectedSet.addAll((Set<String>) opt.getExpected());
        expectedSet.removeAll(values);
        values.addAll(expectedSet);
        cfg.setStringList(opt.getSection(), opt.getSubSection(), opt.getKey(), values);
      }
    }
    return cfg;
  }
}
