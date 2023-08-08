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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.shared.model.IngressConfig;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GerritConfigBuilder extends ConfigBuilder {
  private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?)://.+");

  public GerritConfigBuilder() {
    super("gerrit.config");
  }

  @Override
  void addRequiredOptions(Gerrit gerrit) {
    addRequiredOption(
        new RequiredOption<String>("container", "javaHome", "/usr/lib/jvm/java-11-openjdk"));
    addRequiredOption(
        new RequiredOption<Set<String>>(
            "container",
            "javaOptions",
            Set.of("-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore")));
    addRequiredOption(new RequiredOption<String>("container", "user", "gerrit"));
    addRequiredOption(new RequiredOption<String>("gerrit", "basepath", "git"));
    addRequiredOption(new RequiredOption<String>("cache", "directory", "cache"));
    useReplicaMode(gerrit.getSpec().getMode().equals(GerritMode.REPLICA));

    withSshListenAddress(gerrit);
    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled()) {
      withUrl(ingressConfig.getUrl());
      withSshAdvertisedAddress(gerrit);
    }
  }

  @VisibleForTesting
  GerritConfigBuilder withUrl(String url) {
    addRequiredOption(new RequiredOption<String>("gerrit", "canonicalWebUrl", url));

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
    addRequiredOption(
        new RequiredOption<String>("httpd", "listenUrl", listenUrlBuilder.toString()));
    return this;
  }

  private void withSshListenAddress(Gerrit gerrit) {
    String listenAddress;
    if (gerrit.isSshEnabled()) {
      listenAddress = "*:" + SSH_PORT;
    } else {
      listenAddress = "off";
    }
    addRequiredOption(new RequiredOption<String>("sshd", "listenAddress", listenAddress));
  }

  private void withSshAdvertisedAddress(Gerrit gerrit) {
    if (gerrit.isSshEnabled()) {
      addRequiredOption(
          new RequiredOption<String>(
              "sshd",
              "advertisedAddress",
              gerrit.getSpec().getIngress().getFullHostnameForService(GerritService.getName(gerrit))
                  + ":29418"));
    }
  }

  private GerritConfigBuilder useReplicaMode(boolean isReplica) {
    addRequiredOption(new RequiredOption<Boolean>("container", "replica", isReplica));
    return this;
  }
}
