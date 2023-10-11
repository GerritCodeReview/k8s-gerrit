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

package com.google.gerrit.k8s.operator.v1beta1.gerrit.config;

import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.HTTP_PORT;
import static com.google.gerrit.k8s.operator.gerrit.dependent.GerritStatefulSet.SSH_PORT;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.gerrit.config.ConfigBuilder;
import com.google.gerrit.k8s.operator.gerrit.config.RequiredOption;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.v1beta1.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.v1beta1.api.model.shared.IngressConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Deprecated
public class GerritConfigBuilder extends ConfigBuilder {
  private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?)://.+");

  public GerritConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("gerrit.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.addAll(cacheSection(gerrit));
    requiredOptions.addAll(containerSection(gerrit));
    requiredOptions.addAll(gerritSection(gerrit));
    requiredOptions.addAll(httpdSection(gerrit));
    requiredOptions.addAll(sshdSection(gerrit));
    return requiredOptions;
  }

  private static List<RequiredOption<?>> cacheSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(new RequiredOption<String>("cache", "directory", "cache"));
    return requiredOptions;
  }

  private static List<RequiredOption<?>> containerSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(new RequiredOption<String>("container", "user", "gerrit"));
    requiredOptions.add(
        new RequiredOption<Boolean>(
            "container", "replica", gerrit.getSpec().getMode().equals(GerritMode.REPLICA)));
    requiredOptions.add(
        new RequiredOption<String>("container", "javaHome", "/usr/lib/jvm/java-11-openjdk"));
    requiredOptions.add(javaOptions(gerrit));
    return requiredOptions;
  }

  private static List<RequiredOption<?>> gerritSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    String serverId = gerrit.getSpec().getServerId();
    requiredOptions.add(new RequiredOption<String>("gerrit", "basepath", "git"));
    if (serverId != null && !serverId.isBlank()) {
      requiredOptions.add(new RequiredOption<String>("gerrit", "serverId", serverId));
    }

    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installModule",
              Set.of("com.gerritforge.gerrit.globalrefdb.validation.LibModule")));
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installDbModule",
              Set.of("com.ericsson.gerrit.plugins.highavailability.ValidationModule")));
    }

    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled()) {
      requiredOptions.add(
          new RequiredOption<String>("gerrit", "canonicalWebUrl", ingressConfig.getUrl()));
    }

    return requiredOptions;
  }

  private static List<RequiredOption<?>> httpdSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled()) {
      requiredOptions.add(listenUrl(ingressConfig.getUrl()));
    }
    return requiredOptions;
  }

  private static List<RequiredOption<?>> sshdSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(sshListenAddress(gerrit));
    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled() && gerrit.isSshEnabled()) {
      requiredOptions.add(sshAdvertisedAddress(gerrit));
    }
    return requiredOptions;
  }

  private static RequiredOption<Set<String>> javaOptions(Gerrit gerrit) {
    Set<String> javaOptions = new HashSet<>();
    javaOptions.add("-Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore");
    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      javaOptions.add("-Djava.net.preferIPv4Stack=true");
    }
    if (gerrit.getSpec().getDebug().isEnabled()) {
      javaOptions.add("-Xdebug");
      String debugServerCfg = "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000";
      if (gerrit.getSpec().getDebug().isSuspend()) {
        debugServerCfg = debugServerCfg + ",suspend=y";
      } else {
        debugServerCfg = debugServerCfg + ",suspend=n";
      }
      javaOptions.add(debugServerCfg);
    }
    return new RequiredOption<Set<String>>("container", "javaOptions", javaOptions);
  }

  private static RequiredOption<String> listenUrl(String url) {
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
    return new RequiredOption<String>("httpd", "listenUrl", listenUrlBuilder.toString());
  }

  private static RequiredOption<String> sshListenAddress(Gerrit gerrit) {
    String listenAddress;
    if (gerrit.isSshEnabled()) {
      listenAddress = "*:" + SSH_PORT;
    } else {
      listenAddress = "off";
    }
    return new RequiredOption<String>("sshd", "listenAddress", listenAddress);
  }

  private static RequiredOption<String> sshAdvertisedAddress(Gerrit gerrit) {
    return new RequiredOption<String>(
        "sshd",
        "advertisedAddress",
        gerrit
                .getSpec()
                .getIngress()
                .getFullHostnameForService(GerritService.getName(gerrit.getMetadata().getName()))
            + ":29418");
  }
}
