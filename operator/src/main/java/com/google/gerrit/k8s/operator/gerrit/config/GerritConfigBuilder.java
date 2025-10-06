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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.api.model.gerrit.GerritTemplateSpec.GerritMode;
import com.google.gerrit.k8s.operator.api.model.indexer.GerritIndexer;
import com.google.gerrit.k8s.operator.api.model.shared.ElasticSearchConfig;
import com.google.gerrit.k8s.operator.api.model.shared.EventsBrokerConfig;
import com.google.gerrit.k8s.operator.api.model.shared.GlobalRefDbConfig.RefDatabase;
import com.google.gerrit.k8s.operator.api.model.shared.IndexType;
import com.google.gerrit.k8s.operator.api.model.shared.IngressConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class GerritConfigBuilder extends ConfigBuilder {
  private static final String ES_SECTION_NAME = "elasticsearch";

  public GerritConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("gerrit.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  public GerritConfigBuilder(GerritIndexer gerritIndexer, Gerrit gerrit) {
    super(
        gerritIndexer.getSpec().getConfigFiles().getOrDefault("gerrit.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.addAll(cacheSection(gerrit));
    requiredOptions.addAll(containerSection(gerrit));
    if (gerrit.getSpec().getIndex().getType() == IndexType.ELASTICSEARCH) {
      requiredOptions.addAll(elasticsearchSection(gerrit));
    }
    requiredOptions.addAll(gerritSection(gerrit));
    requiredOptions.addAll(httpdSection(gerrit));
    requiredOptions.addAll(indexSection(gerrit));
    requiredOptions.addAll(pluginsSection(gerrit));
    requiredOptions.addAll(sshdSection(gerrit));
    requiredOptions.addAll(eventsBrokerSection(gerrit));
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

  private static List<RequiredOption<?>> elasticsearchSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    ElasticSearchConfig esConfig = gerrit.getSpec().getIndex().getElasticsearch();
    requiredOptions.add(
        new RequiredOption<String>(ES_SECTION_NAME, "server", esConfig.getServer()));
    if (esConfig.getConfig() != null) {
      try {
        Config parsedEsConfig = new Config();
        parsedEsConfig.fromText(esConfig.getConfig());
        Set<String> sections = parsedEsConfig.getSections();
        if (sections.size() > 1 || !sections.toArray()[0].equals(ES_SECTION_NAME)) {
          throw new IllegalStateException(
              "No section other than `[elasticsearch]` is allowed in the elasticsearch configuration.");
        }
        Set<String> keys = parsedEsConfig.getNames(ES_SECTION_NAME);
        for (String key : keys) {
          if (key.toLowerCase().equals("server")) {
            continue;
          }
          requiredOptions.add(
              new RequiredOption<String>(
                  ES_SECTION_NAME, key, parsedEsConfig.getString(ES_SECTION_NAME, null, key)));
        }
      } catch (ConfigInvalidException e) {
        throw new IllegalStateException("Invalid ElasticSearch config.", e);
      }
    }
    return requiredOptions;
  }

  private static List<RequiredOption<?>> gerritSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    String serverId = gerrit.getSpec().getServerId();
    requiredOptions.add(new RequiredOption<String>("gerrit", "basepath", "git"));
    if (serverId != null && !serverId.isBlank()) {
      requiredOptions.add(new RequiredOption<String>("gerrit", "serverId", serverId));
    }

    if (!gerrit.getSpec().getRefdb().getDatabase().equals(RefDatabase.NONE)
            && gerrit.getSpec().getMode().equals(GerritMode.PRIMARY)
        || gerrit.getSpec().isHighlyAvailablePrimary()) {
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installModule",
              Set.of("com.gerritforge.gerrit.globalrefdb.validation.LibModule")));
    }
    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installDbModule",
              Set.of("com.ericsson.gerrit.plugins.highavailability.ValidationModule")));
    }
    if (gerrit.getSpec().getIndex().getType() == IndexType.ELASTICSEARCH) {
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installIndexModule",
              Set.of(
                  gerrit.getSpec().getMode() == GerritMode.REPLICA
                      ? "com.google.gerrit.elasticsearch.ReplicaElasticIndexModule"
                      : "com.google.gerrit.elasticsearch.ElasticIndexModule")));
    }

    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled()) {
      requiredOptions.add(
          new RequiredOption<String>("gerrit", "canonicalWebUrl", ingressConfig.getUrl()));
    }

    if (gerrit.getSpec().getEventsBroker().getBrokerType() != EventsBrokerConfig.BrokerType.NONE) {
      requiredOptions.add(
          new RequiredOption<Set<String>>(
              "gerrit",
              "installModule",
              Set.of("com.gerritforge.gerrit.eventbroker.BrokerApiModule")));
    }

    return requiredOptions;
  }

  private static List<RequiredOption<?>> httpdSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    IngressConfig ingressConfig = gerrit.getSpec().getIngress();
    if (ingressConfig.isEnabled()) {
      requiredOptions.add(listenUrl(ingressConfig));
    }
    return requiredOptions;
  }

  private static List<RequiredOption<?>> indexSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    IndexType indexType = gerrit.getSpec().getIndex().getType();
    GerritMode gerritMode = gerrit.getSpec().getMode();
    if (indexType == IndexType.ELASTICSEARCH && gerritMode == GerritMode.REPLICA) {
      requiredOptions.add(
          new RequiredOption<Boolean>("index", "scheduledIndexer", "enabled", false));
      requiredOptions.add(
          new RequiredOption<Boolean>("index", "scheduledIndexer", "runOnStartup", false));
    }
    return requiredOptions;
  }

  private static List<RequiredOption<?>> pluginsSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    Set<String> mandatoryPlugins = new HashSet<>();
    mandatoryPlugins.add("healthcheck");
    if (gerrit.getSpec().isHighlyAvailablePrimary()) {
      mandatoryPlugins.add("high-availability");
    }
    if (gerrit.getSpec().getIndex().getType() == IndexType.ELASTICSEARCH) {
      mandatoryPlugins.add("index-elasticsearch");
    }
    RefDatabase refDb = gerrit.getSpec().getRefdb().getDatabase();
    switch (refDb) {
      case NONE:
        break;
      case ZOOKEEPER:
        mandatoryPlugins.add("zookeeper-refdb");
        break;
      case SPANNER:
        mandatoryPlugins.add("spanner-refdb");
        break;
      default:
        throw new IllegalStateException("Unknown refdb database type: " + refDb);
    }
    if (gerrit.getSpec().getEventsBroker().getBrokerType() == EventsBrokerConfig.BrokerType.KAFKA) {
      mandatoryPlugins.add("events-kafka");
    }
    requiredOptions.add(new RequiredOption<Set<String>>("plugins", "mandatory", mandatoryPlugins));
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
    javaOptions.add("-Djava.io.tmpdir=/var/gerrit/tmp/java");
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

  private static RequiredOption<String> listenUrl(IngressConfig ingressConfig) {
    StringBuilder listenUrlBuilder = new StringBuilder();
    listenUrlBuilder.append("proxy-");
    listenUrlBuilder.append(ingressConfig.isTlsEnabled() ? "https" : "http");
    listenUrlBuilder.append("://*:");
    listenUrlBuilder.append(HTTP_PORT);
    listenUrlBuilder.append(ingressConfig.getPathPrefix());
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
    int port = gerrit.getSpec().getSshdAdvertisedReadPort();
    if (port == 0) {
      port = gerrit.getSpec().getService().getSshPort();
    }
    return new RequiredOption<String>(
        "sshd", "advertisedAddress", gerrit.getSpec().getIngress().getHost() + ":" + port);
  }

  private static List<RequiredOption<?>> eventsBrokerSection(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    EventsBrokerConfig eventsBroker = gerrit.getSpec().getEventsBroker();
    if (eventsBroker.getBrokerType() == EventsBrokerConfig.BrokerType.KAFKA) {
      requiredOptions.add(
          new RequiredOption<String>(
              "plugin",
              "events-kafka",
              "bootstrapServers",
              eventsBroker.getKafkaConfig().getConnectString()));
      requiredOptions.add(
          new RequiredOption<String>("plugin", "events-kafka", "numberOfSubscribers", "7"));
      requiredOptions.add(
          new RequiredOption<Boolean>("plugin", "events-kafka", "sendStreamEvents", false));
      requiredOptions.add(
          new RequiredOption<String>("plugin", "events-kafka", "groupId", "EVENT_BROKER_GROUP_ID"));
      requiredOptions.add(
          new RequiredOption<String>(
              "plugin", "events-kafka", "topic", "stream_event_" + gerrit.getSpec().getServerId()));
    }
    return requiredOptions;
  }
}
