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


class InitConfig:
    def __init__(self):
        self.downloaded_plugins = []
        self.plugin_cache_enabled = False
        self.packaged_plugins = set()
        self.install_as_library = set()
        self.plugin_cache_dir = None

        self.ca_cert_path = True

    def parse(self, config_file):
        if not os.path.exists(config_file):
            raise FileNotFoundError(f"Could not find config file: {config_file}")

        with open(config_file, "r", encoding="utf-8") as f:
            config = yaml.load(f, Loader=yaml.SafeLoader)

        if config is None:
            raise ValueError(f"Invalid config-file: {config_file}")

        if "downloadedPlugins" in config:
            self.downloaded_plugins = config["downloadedPlugins"]
        if "packagedPlugins" in config:
            self.packaged_plugins = set(config["packagedPlugins"])
        if "installAsLibrary" in config:
            self.install_as_library = set(config["installAsLibrary"])
        #DEPRECATED: `pluginCache` was deprecated in favor of `pluginCacheEnabled`
        if "pluginCache" in config:
            self.plugin_cache_enabled = config["pluginCache"]
        if "pluginCacheEnabled" in config:
            self.plugin_cache_enabled = config["pluginCacheEnabled"]
        if "pluginCacheDir" in config and config["pluginCacheDir"]:
            self.plugin_cache_dir = config["pluginCacheDir"]

        if "caCertPath" in config:
            self.ca_cert_path = config["caCertPath"]

        return self

    def get_all_configured_plugins(self):
        plugins = set(self.packaged_plugins)
        plugins.update([p["name"] for p in self.downloaded_plugins])
        return plugins
