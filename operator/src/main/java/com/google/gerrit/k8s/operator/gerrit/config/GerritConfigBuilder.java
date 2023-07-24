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

import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@SuppressWarnings("rawtypes")
public class GerritConfigBuilder {
  private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?)://.+");
  private List<RequiredOption> requiredOptions = new ArrayList<>(setupStaticRequiredOptions());
  private Config cfg;

  private static List<RequiredOption> setupStaticRequiredOptions() {
    List<RequiredOption> requiredOptions = new ArrayList<>();
    requiredOptions.add(
        new RequiredOption<String>("container", "javaHome", "/usr/lib/jvm/java-11-openjdk"));
    requiredOptions.add(
        new RequiredOption<Set<String>>(
            "container",
            "javaOptions",
            Set.of("-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore")));
    requiredOptions.add(new RequiredOption<String>("container", "user", "gerrit"));
    requiredOptions.add(new RequiredOption<String>("gerrit", "basepath", "git"));
    requiredOptions.add(new RequiredOption<String>("cache", "directory", "cache"));
    return requiredOptions;
  }

  public GerritConfigBuilder forGerrit(Gerrit gerrit) {
    String gerritConfig = gerrit.getSpec().getConfigFiles().getOrDefault("gerrit.config", "");

    withConfig(gerritConfig);
    useReplicaMode(gerrit.getSpec().getMode().equals(GerritMode.REPLICA));

    boolean ingressEnabled = gerrit.getSpec().getIngress().isEnabled();

    if (ingressEnabled) {
      withUrl(gerrit.getSpec().getIngress().getUrl(GerritService.getName(gerrit)));
      withSsh(
          gerrit.getSpec().getIngress().getSsh().isEnabled(),
          gerrit.getSpec().getIngress().getFullHostnameForService(GerritService.getName(gerrit))
              + ":29418");
    } else {
      withUrl(GerritService.getUrl(gerrit));
    }

    return this;
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
    requiredOptions.add(new RequiredOption<String>("gerrit", "canonicalWebUrl", url));

    StringBuilder listenUrlBuilder = new StringBuilder();
    listenUrlBuilder.append("proxy-");
    Matcher protocolMatcher = PROTOCOL_PATTERN.matcher(url);
    if (protocolMatcher.matches()) {
      listenUrlBuilder.append(protocolMatcher.group(1));
    } else {
      throw new IllegalStateException(
          String.format("Unknown protocol used for canonicalWebUrl: %s", url));
    }
    listenUrlBuilder.append("://*:");
    listenUrlBuilder.append(HTTP_PORT);
    listenUrlBuilder.append("/");
    requiredOptions.add(
        new RequiredOption<String>("httpd", "listenUrl", listenUrlBuilder.toString()));
    return this;
  }

  public GerritConfigBuilder withSsh(boolean enabled) {
    String listenAddress;
    if (enabled) {
      listenAddress = "*:" + SSH_PORT;
    } else {
      listenAddress = "off";
    }
    requiredOptions.add(new RequiredOption<String>("sshd", "listenAddress", listenAddress));
    return this;
  }

  public GerritConfigBuilder withSsh(boolean enabled, String advertisedAddress) {
    requiredOptions.add(new RequiredOption<String>("sshd", "advertisedAddress", advertisedAddress));
    return withSsh(enabled);
  }

  public GerritConfigBuilder useReplicaMode(boolean isReplica) {
    requiredOptions.add(new RequiredOption<Boolean>("container", "replica", isReplica));
    return this;
  }

  public Config build() {
    GerritConfigValidator configValidator = new GerritConfigValidator(requiredOptions);
    try {
      configValidator.check(cfg);
    } catch (InvalidGerritConfigException e) {
      throw new IllegalStateException(e);
    }
    setRequiredOptions();
    return cfg;
  }

  public void validate() throws InvalidGerritConfigException {
    new GerritConfigValidator(requiredOptions).check(cfg);
  }

  @SuppressWarnings("unchecked")
  private void setRequiredOptions() {
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
        List<String> expectedSet = new ArrayList<String>();
        expectedSet.addAll((Set<String>) opt.getExpected());
        expectedSet.removeAll(values);
        values.addAll(expectedSet);
        cfg.setStringList(opt.getSection(), opt.getSubSection(), opt.getKey(), values);
      }
    }
  }

  public List<RequiredOption> getRequiredOptions() {
    return requiredOptions;
  }
}
