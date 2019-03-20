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

import requests

from init_config import InitConfig
from log import get_logger

LOG = get_logger("init")
MAX_LOCK_LIFETIME = 60
MAX_CACHED_VERSIONS = 5


class InvalidPluginException(Exception):
    """ Exception to be raised, if the downloaded plugin is not valid. """


class AbstractPluginInstaller(ABC):
    def __init__(self, site, config):
        self.site = site
        self.config = config

        self.plugin_dir = os.path.join(site, "plugins")
        self.plugins_changed = False

    def _create_plugins_dir(self):
        if not os.path.exists(self.plugin_dir):
            os.makedirs(self.plugin_dir)
            LOG.info("Created plugin installation directory: %s", self.plugin_dir)

    def _get_installed_plugins(self):
        if os.path.exists(self.plugin_dir):
            return [f for f in os.listdir(self.plugin_dir) if f.endswith(".jar")]

        return list()

    @staticmethod
    def _get_file_sha(file):
        file_hash = hashlib.sha1()
        with open(file, "rb") as f:
            while True:
                chunk = f.read(64000)
                if not chunk:
                    break
                file_hash.update(chunk)

        LOG.debug("SHA1 of file '%s' is %s", file, file_hash.hexdigest())

        return file_hash.hexdigest()

    def _remove_unwanted_plugins(self):
        wanted_plugins = [plugin["name"] for plugin in self.config.downloaded_plugins]
        wanted_plugins.extend(self.config.packaged_plugins)
        for plugin in self._get_installed_plugins():
            if os.path.splitext(plugin)[0] not in wanted_plugins:
                os.remove(os.path.join(self.plugin_dir, plugin))
                LOG.info("Removed plugin %s", plugin)

    def execute(self):
        self._create_plugins_dir()
        self._remove_unwanted_plugins()

        for plugin in self.config.downloaded_plugins:
            self._install_plugin(plugin)

    def _download_plugin(self, plugin, target):
        LOG.info("Downloading %s plugin to %s", plugin["name"], target)
        response = requests.get(plugin["url"])
        with open(target, "wb") as f:
            f.write(response.content)

        file_sha = self._get_file_sha(target)

        if file_sha != plugin["sha1"]:
            os.remove(target)
            raise InvalidPluginException(
                (
                    "SHA1 of downloaded file (%s) did not match expected SHA1 (%s). "
                    "Removed downloaded file (%s)"
                )
                % (file_sha, plugin["sha1"], target)
            )

    @abstractmethod
    def _install_plugin(self, plugin):
        pass


class PluginInstaller(AbstractPluginInstaller):
    def _install_plugin(self, plugin):
        target = os.path.join(self.plugin_dir, "%s.jar" % plugin["name"])
        if os.path.exists(target) and self._get_file_sha(target) == plugin["sha1"]:
            return

        self._download_plugin(plugin, target)

        self.plugins_changed = True


class CachedPluginInstaller(AbstractPluginInstaller):
    @staticmethod
    def _cleanup_cache(plugin_cache_dir):
        cached_files = [
            os.path.join(plugin_cache_dir, f) for f in os.listdir(plugin_cache_dir)
        ]
        while len(cached_files) > MAX_CACHED_VERSIONS:
            oldest_file = min(cached_files, key=os.path.getctime)
            LOG.info(
                "Too many cached files in %s. Removing file %s",
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

    def _get_cached_plugin_path(self, plugin):
        return os.path.join(
            self.config.plugin_cache_dir,
            plugin["name"],
            "%s-%s.jar" % (plugin["name"], plugin["sha1"]),
        )

    def _install_from_cache_or_download(self, plugin, target):
        cached_plugin_path = self._get_cached_plugin_path(plugin)

        if os.path.exists(cached_plugin_path):
            LOG.info("Installing %s plugin from cache.", plugin["name"])
        else:
            LOG.info("%s not found in cache. Downloading it.", plugin["name"])
            download_target = self._get_cached_plugin_path(plugin)
            self._create_plugin_cache_dir(os.path.dirname(target))

            lock_path = "%s.lock" % download_target
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
                self._download_plugin(plugin, download_target)
            finally:
                self._remove_download_lock(lock_path)

        os.symlink(cached_plugin_path, target)
        self._cleanup_cache(os.path.dirname(target))

    def _install_plugin(self, plugin):
        install_path = os.path.join(self.plugin_dir, "%s.jar" % plugin["name"])
        if (
            os.path.exists(install_path)
            and self._get_file_sha(install_path) == plugin["sha1"]
        ):
            return

        self.plugins_changed = True
        self._install_from_cache_or_download(plugin, install_path)

    @staticmethod
    def _remove_download_lock(lock_path):
        os.remove(lock_path)
        LOG.debug("Removed download lock %s", lock_path)


def get_installer(site, config):
    plugin_installer = (
        CachedPluginInstaller if config.plugin_cache_enabled else PluginInstaller
    )
    return plugin_installer(site, config)


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
        "-c",
        "--config",
        help="Path to configuration file for init process.",
        dest="config",
        action="store",
        required=True,
    )
    args = parser.parse_args()

    config = InitConfig().parse(args.config)
    get_installer(args.site, config).execute()
