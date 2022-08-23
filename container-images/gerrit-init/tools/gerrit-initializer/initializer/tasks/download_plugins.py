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

import hashlib
import os
import shutil
import time

from abc import ABC, abstractmethod
from zipfile import ZipFile

import requests

from ..helpers import log

LOG = log.get_logger("init")
MAX_LOCK_LIFETIME = 60
MAX_CACHED_VERSIONS = 5


class InvalidPluginException(Exception):
    """Exception to be raised, if the downloaded plugin is not valid."""


class AbstractPluginInstaller(ABC):
    def __init__(self, site, config):
        self.site = site
        self.config = config

        self.required_plugins = self._get_required_plugins()

        self.plugin_dir = os.path.join(site, "plugins")
        self.lib_dir = os.path.join(site, "lib")
        self.plugins_changed = False

    def _create_plugins_dir(self):
        if not os.path.exists(self.plugin_dir):
            os.makedirs(self.plugin_dir)
            LOG.info("Created plugin installation directory: %s", self.plugin_dir)

    def _get_installed_plugins(self):
        if os.path.exists(self.plugin_dir):
            return [f for f in os.listdir(self.plugin_dir) if f.endswith(".jar")]

        return []

    def _get_required_plugins(self):
        required = [
            os.path.splitext(f)[0]
            for f in os.listdir("/var/plugins")
            if f.endswith(".jar")
        ]
        return list(
            filter(
                lambda x: x not in self.config.get_all_configured_plugins(), required
            )
        )

    def _install_plugins_from_container(self):
        source_dir = "/var/plugins"
        for plugin in self.required_plugins:
            source_file = os.path.join(source_dir, plugin + ".jar")
            target_file = os.path.join(self.plugin_dir, plugin + ".jar")
            if os.path.exists(target_file) and self._get_file_sha(
                source_file
            ) == self._get_file_sha(target_file):
                continue

            shutil.copyfile(source_file, target_file)
            self.plugins_changed = True

    def _install_plugins_from_war(self):
        for plugin in self.config.packaged_plugins:
            LOG.info("Installing packaged plugin %s.", plugin)
            with ZipFile("/var/war/gerrit.war", "r") as war:
                war.extract(f"WEB-INF/plugins/{plugin}.jar", self.plugin_dir)

            os.rename(
                f"{self.plugin_dir}/WEB-INF/plugins/{plugin}.jar",
                os.path.join(self.plugin_dir, f"{plugin}.jar"),
            )
        shutil.rmtree(os.path.join(self.plugin_dir, "WEB-INF"), ignore_errors=True)

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
        wanted_plugins = list(self.config.get_all_configured_plugins())
        wanted_plugins.extend(self.required_plugins)
        for plugin in self._get_installed_plugins():
            if os.path.splitext(plugin)[0] not in wanted_plugins:
                os.remove(os.path.join(self.plugin_dir, plugin))
                LOG.info("Removed plugin %s", plugin)

    def _symlink_plugins_to_lib(self):
        if not os.path.exists(self.lib_dir):
            os.makedirs(self.lib_dir)
        else:
            for f in os.listdir(self.lib_dir):
                path = os.path.join(self.lib_dir, f)
                if (
                    os.path.islink(path)
                    and os.path.splitext(f)[0] not in self.config.install_as_library
                ):
                    os.unlink(path)
                    LOG.info("Removed symlink %s", f)
        for lib in self.config.install_as_library:
            plugin_path = os.path.join(self.plugin_dir, f"{lib}.jar")
            if os.path.exists(plugin_path):
                try:
                    os.symlink(plugin_path, os.path.join(self.lib_dir, f"{lib}.jar"))
                except FileExistsError:
                    continue
            else:
                raise FileNotFoundError(
                    f"Could not find plugin {lib} to symlink to lib-directory."
                )

    def execute(self):
        self._create_plugins_dir()
        self._remove_unwanted_plugins()
        self._install_plugins_from_container()
        self._install_plugins_from_war()

        for plugin in self.config.downloaded_plugins:
            self._install_plugin(plugin)

        self._symlink_plugins_to_lib()

    def _download_plugin(self, plugin, target):
        LOG.info("Downloading %s plugin to %s", plugin["name"], target)
        try:
            response = requests.get(plugin["url"])
        except requests.exceptions.SSLError:
            response = requests.get(plugin["url"], verify=self.config.ca_cert_path)

        with open(target, "wb") as f:
            f.write(response.content)

        file_sha = self._get_file_sha(target)

        if file_sha != plugin["sha1"]:
            os.remove(target)
            raise InvalidPluginException(
                (
                    f"SHA1 of downloaded file ({file_sha}) did not match "
                    f"expected SHA1 ({plugin['sha1']}). "
                    f"Removed downloaded file ({target})"
                )
            )

    @abstractmethod
    def _install_plugin(self, plugin):
        pass


class PluginInstaller(AbstractPluginInstaller):
    def _install_plugin(self, plugin):
        target = os.path.join(self.plugin_dir, f"{plugin['name']}.jar")
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
        with open(lock_path, "w", encoding="utf-8") as f:
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
            f"{plugin['name']}-{plugin['sha1']}.jar",
        )

    def _install_from_cache_or_download(self, plugin, target):
        cached_plugin_path = self._get_cached_plugin_path(plugin)

        if os.path.exists(cached_plugin_path):
            LOG.info("Installing %s plugin from cache.", plugin["name"])
        else:
            LOG.info("%s not found in cache. Downloading it.", plugin["name"])
            download_target = self._get_cached_plugin_path(plugin)
            self._create_plugin_cache_dir(os.path.dirname(download_target))

            lock_path = f"{download_target}.lock"
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
        install_path = os.path.join(self.plugin_dir, f"{plugin['name']}.jar")
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
