// Copyright (C) 2024 The Android Open Source Project
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
import com.google.gerrit.k8s.operator.api.model.gerrit.Gerrit;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritHeadlessService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

public class PullReplicationPluginConfigBuilder extends ConfigBuilder {

  public PullReplicationPluginConfigBuilder(Gerrit gerrit) {
    super(
        gerrit.getSpec().getConfigFiles().getOrDefault("replication.config", ""),
        ImmutableList.copyOf(collectRequiredOptions(gerrit)));
  }

  private static List<RequiredOption<?>> collectRequiredOptions(Gerrit gerrit) {
    List<RequiredOption<?>> requiredOptions = new ArrayList<>();
    requiredOptions.add(
        new RequiredOption<String>(
            "replication", "eventbrokertopic", "stream_event_" + gerrit.getSpec().getServerId()));
    requiredOptions.add(
        new RequiredOption<String>("replication", "eventBrokerGroupId", "EVENT_BROKER_GROUP_ID"));
    requiredOptions.add(new RequiredOption<Boolean>("replication", "consumeStreamEvents", false));
    requiredOptions.add(new RequiredOption<String>("replication", "syncRefs", "ALL REFS ASYNC"));
    return requiredOptions;
  }

  public Config makeRemoteSections(Config config, Gerrit gerrit) {
    String headlessServiceHostname = new GerritHeadlessService().getHostname(gerrit);
    for (int i = 0; i < gerrit.getSpec().getReplicas(); i++) {
      buildRemoteSection(config, "gerrit-" + i, headlessServiceHostname);
    }
    config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, null);
    return config;
  }

  private static void buildRemoteSection(
      Config config, String newRemoteName, String headlessServiceHostName) {
    String remoteSection = ConfigConstants.CONFIG_REMOTE_SECTION;

    // Set url and apiUrl for the remote section
    config.setString(
        remoteSection,
        newRemoteName,
        "url",
        "http://" + newRemoteName + "." + headlessServiceHostName + ":8080/${name}.git");
    config.setString(
        remoteSection,
        newRemoteName,
        "apiUrl",
        "http://" + newRemoteName + "." + headlessServiceHostName + ":8080");

    // Get all keys in the original [remote] section
    Set<String> keys = config.getNames(remoteSection, true);

    // Copy each key-value pair to the new remote section
    for (String key : keys) {
      String value = config.getString(remoteSection, null, key);
      config.setString(remoteSection, newRemoteName, key, value);
    }
  }
}
