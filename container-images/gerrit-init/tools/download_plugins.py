#!/usr/bin/python3

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

import argparse
import hashlib
import os
import time

from abc import ABC, abstractmethod
from shutil import copyfile

import requests
import yaml

from log import get_logger

LOG = get_logger("init")
MAX_LOCK_LIFETIME = 60
MAX_CACHED_VERSIONS = 5


class InvalidPluginException(Exception):
    """ Exception to be raised, if the downloaded plugin is not valid. """


class AbstractPluginInstaller(ABC):
    def __init__(self, site, config_file):
        self.site = site
        self.plugin_dir = os.path.join(site, "plugins")
        self.config_file = config_file

    def _create_plugins_dir(self):
        if not os.path.exists(self.plugin_dir):
            os.makedirs(self.plugin_dir)
            LOG.info("Created plugin installation directory: %s", self.plugin_dir)

    def _get_installed_plugins(self):
        if os.path.exists(self.plugin_dir):
            return [f for f in os.listdir(self.plugin_dir) if f.endswith(".jar")]

        return list()

    def _create_init_lock(self):
        lock_path = os.path.join(self.site, "init.lock")
        if not os.path.exists(lock_path):
            open(lock_path, "a").close()
            LOG.info("Created %s to trigger Gerrit init.", lock_path)

    def _parse_config_file(self):
        with open(self.config_file, "r") as f:
            config = yaml.load(f, Loader=yaml.SafeLoader)
            return config if config is not None else list()

    @staticmethod
    def _compare_plugin_sha(plugin, plugin_file):
        file_hash = hashlib.sha1()
        with open(plugin_file, "rb") as f:
            while True:
                chunk = f.read(64000)
                if not chunk:
                    break
                file_hash.update(chunk)

        LOG.debug(
            "Expected SHA1 '%s' and got '%s' for plugin %s",
            plugin["sha1"],
            file_hash.hexdigest(),
            plugin["name"],
        )

        return plugin["sha1"] == file_hash.hexdigest()

    def _remove_unwanted_plugins(self, config):
        wanted_plugins = [plugin["name"] for plugin in config]
        for plugin in self._get_installed_plugins():
            if os.path.splitext(plugin)[0] not in wanted_plugins:
                os.remove(os.path.join(self.plugin_dir, plugin))
                LOG.info("Removed plugin %s", plugin)

    def execute(self):
        config = self._parse_config_file()
        LOG.debug("Parsed config: %s", config)
        self._create_plugins_dir()
        self._remove_unwanted_plugins(config)

        for plugin in config:
            if "url" in plugin:
                self._install_plugin(plugin)
            else:
                LOG.info(
                    "%s plugin is packaged in the war-file and will be installed in a later step",
                    plugin["name"],
                )

    @abstractmethod
    def _download_plugin(self, plugin, target):
        pass

    @abstractmethod
    def _install_plugin(self, plugin):
        pass


class PluginInstaller(AbstractPluginInstaller):
    def _download_plugin(self, plugin, target):
        LOG.info("Downloading %s plugin to %s", plugin["name"], target)
        response = requests.get(plugin["url"])
        with open(target, "wb") as f:
            f.write(response.content)

    def _install_plugin(self, plugin):
        target = os.path.join(self.plugin_dir, "%s.jar" % plugin["name"])
        if os.path.exists(target) and self._compare_plugin_sha(plugin, target):
            return

        self._download_plugin(plugin, target)

        if not self._compare_plugin_sha(plugin, target):
            os.remove(target)
            LOG.error("SHA1 not as expected. Removed downloaded file (%s).", target)
            raise InvalidPluginException(
                "SHA1 of downloaded file did not match expected SHA1 (%s)."
                % (plugin["sha1"])
            )

        self._create_init_lock()


class CachedPluginInstaller(AbstractPluginInstaller):
    def __init__(self, site, config_file, cache_dir):
        super().__init__(site, config_file)
        self.cache_dir = cache_dir

    @staticmethod
    def _cleanup_cache(plugin_cache_dir):
        cached_files = [
            os.path.join(plugin_cache_dir, f) for f in os.listdir(plugin_cache_dir)
        ]
        while len(cached_files) > MAX_CACHED_VERSIONS:
            oldest_file = min(cached_files, key=os.path.getctime)
            LOG.info(
                "Too many cahced files in %s. Removing file %s",
                plugin_cache_dir,
                oldest_file,
            )
            os.remove(oldest_file)
            cached_files.remove(oldest_file)

    @staticmethod
    def _create_download_lock(lock_path):
        with open(lock_path, "w") as f:
            f.write(os.environ["HOSTNAME"])
            LOG.debug("Created download lock %s", lock_path)

    @staticmethod
    def _create_plugin_cache_dir(plugin_cache_dir):
        if not os.path.exists(plugin_cache_dir):
            os.makedirs(plugin_cache_dir)
            LOG.info("Created cache directory %s", plugin_cache_dir)

    def _download_plugin(self, plugin, target):
        self._create_plugin_cache_dir(os.path.dirname(target))

        lock_path = "%s.lock" % target
        while os.path.exists(lock_path):
            LOG.info(
                "Download lock found (%s). Waiting %d seconds for it to be released.",
                lock_path,
                MAX_LOCK_LIFETIME,
            )
            lock_timestamp = os.path.getmtime(lock_path)
            if time.time() > lock_timestamp + MAX_LOCK_LIFETIME:
                LOG.info("Stale download lock found (%s).", lock_path)
                self._remove_download_lock(lock_path)

        self._create_download_lock(lock_path)

        try:
            LOG.info("Downloading %s plugin to %s", plugin["name"], target)
            response = requests.get(plugin["url"])
            with open(target, "wb") as f:
                f.write(response.content)

            if not self._compare_plugin_sha(plugin, target):
                os.remove(target)
                LOG.error("SHA1 not as expected. Removed downloaded file (%s).", target)
                raise InvalidPluginException(
                    "SHA1 of downloaded file did not match expected SHA1 (%s)."
                    % (plugin["sha1"])
                )
        finally:
            self._remove_download_lock(lock_path)

        self._cleanup_cache(os.path.dirname(target))

    def _get_cached_plugin_path(self, plugin):
        return os.path.join(
            self.cache_dir,
            plugin["name"],
            "%s-%s.jar" % (plugin["name"], plugin["sha1"]),
        )

    def _install_from_cache(self, plugin, target):
        cached_plugin_path = self._get_cached_plugin_path(plugin)

        if os.path.exists(cached_plugin_path):
            LOG.info("Installing %s plugin from cache.", plugin["name"])
            copyfile(cached_plugin_path, target)
            return target

        return None

    def _install_plugin(self, plugin):
        install_path = os.path.join(self.plugin_dir, "%s.jar" % plugin["name"])
        if os.path.exists(install_path) and self._compare_plugin_sha(
            plugin, install_path
        ):
            return

        self._create_init_lock()

        if self._install_from_cache(plugin, install_path):
            return

        download_target = self._get_cached_plugin_path(plugin)
        self._download_plugin(plugin, download_target)
        self._install_from_cache(plugin, install_path)

    @staticmethod
    def _remove_download_lock(lock_path):
        os.remove(lock_path)
        LOG.debug("Removed download lock %s", lock_path)


# pylint: disable=C0103
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-s",
        "--site",
        help="Path to Gerrit site",
        dest="site",
        action="store",
        default="/var/gerrit",
        required=True,
    )
    parser.add_argument(
        "-f",
        "--file",
        help="Path to plugin-config file.",
        dest="config",
        action="store",
        required=True,
    )
    parser.add_argument(
        "-c", "--cache", help="Path to plugin-cache.", dest="cache_dir", action="store"
    )
    parser.add_argument(
        "-n",
        "--nocache",
        help="Whether to enforce download.",
        dest="no_cache",
        action="store_true",
    )
    args = parser.parse_args()

if args.no_cache:
    PluginInstaller(args.site, args.config).execute()
else:
    CachedPluginInstaller(args.site, args.config, args.cache_dir).execute()
