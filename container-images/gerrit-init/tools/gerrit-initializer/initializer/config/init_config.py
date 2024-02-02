# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os.path

import yaml

from .cluster_mode import ClusterMode


class InitConfig:
    def __init__(self):
        self.plugins = []
        self.libs = []
        self.plugin_cache_enabled = False
        self.plugin_cache_dir = None

        self.ca_cert_path = True

        self.is_ha = False
        self.refdb = False
        self.cluster_mode = ClusterMode.HIGH_AVAILABILITY

    def parse(self, config_file):
        if not os.path.exists(config_file):
            raise FileNotFoundError(f"Could not find config file: {config_file}")

        with open(config_file, "r", encoding="utf-8") as f:
            config = yaml.load(f, Loader=yaml.SafeLoader)

        if config is None:
            raise ValueError(f"Invalid config-file: {config_file}")

        if "plugins" in config:
            self.plugins = config["plugins"]
        if "libs" in config:
            self.libs = config["libs"]
        # DEPRECATED: `pluginCache` was deprecated in favor of `pluginCacheEnabled`
        if "pluginCache" in config:
            self.plugin_cache_enabled = config["pluginCache"]
        if "pluginCacheEnabled" in config:
            self.plugin_cache_enabled = config["pluginCacheEnabled"]
        if "pluginCacheDir" in config and config["pluginCacheDir"]:
            self.plugin_cache_dir = config["pluginCacheDir"]

        if "caCertPath" in config:
            self.ca_cert_path = config["caCertPath"]

        self.is_ha = "highAvailability" in config and config["highAvailability"]

        if "clusterMode" in config:
            self.cluster_mode = config["clusterMode"]

        if "refdb" in config:
            self.refdb = config["refdb"]

        return self

    def get_plugins(self):
        return self.plugins

    def get_plugin_names(self):
        return set([p["name"] for p in self.plugins])

    def get_libs(self):
        return self.libs

    def get_lib_names(self):
        return set([p["name"] for p in self.libs])

    def get_packaged_plugins(self):
        return list(filter(lambda x: "url" not in x, self.plugins))

    def get_downloaded_plugins(self):
        return list(filter(lambda x: "url" in x, self.plugins))

    def get_plugins_installed_as_lib(self):
        return [
            lib["name"]
            for lib in list(
                filter(
                    lambda x: "installAsLibrary" in x and x["installAsLibrary"],
                    self.plugins,
                )
            )
        ]
