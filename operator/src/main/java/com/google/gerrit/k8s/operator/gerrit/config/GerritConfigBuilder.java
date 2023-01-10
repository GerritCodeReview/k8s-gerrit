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
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class GerritConfigBuilder {
  private List<RequiredOption<?>> requiredOptions = setupStaticRequiredOptions();
  private Config cfg;

  private static List<RequiredOption<?>> setupStaticRequiredOptions() {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(
        new RequiredStringOption("container", "javaHome", "/usr/lib/jvm/java-11-openjdk"));
    requiredOptions.add(
        new RequiredStringListOption(
            "container",
            "javaOptions",
            List.of("-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore")));
    requiredOptions.add(new RequiredStringOption("container", "user", "gerrit"));
    requiredOptions.add(new RequiredStringOption("gerrit", "basepath", "git"));
    requiredOptions.add(new RequiredBooleanOption("index", "onlineUpgrade", false));
    return requiredOptions;
  }

  public GerritConfigBuilder withConfig(String text) {
    Config cfg = new Config();
    try {
      cfg.fromText(text);
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("The provided gerrit.config is invalid.");
    }

    return withConfig(cfg);
  }

  public GerritConfigBuilder withConfig(Config cfg) {
    this.cfg = cfg;
    return this;
  }

  public GerritConfigBuilder withUrl(String url) {
    this.requiredOptions.add(new RequiredStringOption("gerrit", "canonicalWebUrl", url));
    return this;
  }

  public GerritConfigBuilder useReplicaMode(boolean isReplica) {
    this.requiredOptions.add(new RequiredBooleanOption("container", "replica", isReplica));
    return this;
  }

  public Config build() {
    GerritConfigValidator configValidator = new GerritConfigValidator(requiredOptions);
    configValidator.check(cfg);
    setRequiredOptions();
    return cfg;
  }

  private void setRequiredOptions() {
    for (RequiredOption<?> opt : requiredOptions) {
      opt.set(cfg);
    }
  }

  public List<RequiredOption<?>> getRequiredOptions() {
    return requiredOptions;
  }
}
