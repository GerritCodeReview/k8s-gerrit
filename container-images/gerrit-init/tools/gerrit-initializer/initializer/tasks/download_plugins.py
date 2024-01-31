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

REQUIRED_PLUGINS = ["healthcheck"]
REQUIRED_HA_PLUGINS = ["high-availability"]
REQUIRED_HA_LIBS = ["high-availability", "global-refdb"]


class InvalidPluginException(Exception):
    """Exception to be raised, if the downloaded plugin is not valid."""


class MissingRequiredPluginException(Exception):
    """Exception to be raised, if the downloaded plugin is not valid."""


class AbstractPluginInstaller(ABC):
    def __init__(self, site, config):
        self.site = site
        self.config = config

        self.required_plugins = self._get_required_plugins()
        self.required_libs = self._get_required_libs()

        self.plugin_dir = os.path.join(site, "plugins")
        self.lib_dir = os.path.join(site, "lib")
        self.plugins_changed = False

    def _create_plugins_dir(self):
        if not os.path.exists(self.plugin_dir):
            os.makedirs(self.plugin_dir)
            LOG.info("Created plugin installation directory: %s", self.plugin_dir)

    def _create_lib_dir(self):
        if not os.path.exists(self.lib_dir):
            os.makedirs(self.lib_dir)
            LOG.info("Created lib installation directory: %s", self.lib_dir)

    def _get_installed_plugins(self):
        return self._get_installed_jars(self.plugin_dir)

    def _get_installed_libs(self):
        return self._get_installed_jars(self.lib_dir)

    @staticmethod
    def _get_installed_jars(dir):
        if os.path.exists(dir):
            return [f for f in os.listdir(dir) if f.endswith(".jar")]

        return []

    def _get_required_plugins(self):
        required = REQUIRED_PLUGINS.copy()
        if self.config.is_ha:
            required.extend(REQUIRED_HA_PLUGINS)
        if self.config.refdb:
            required.append(f"{self.config.refdb}-refdb")
        LOG.info("Requiring plugins: %s", required)
        return required

    def _get_required_libs(self):
        required = []
        if self.config.is_ha:
            required.extend(REQUIRED_HA_LIBS)
        elif self.config.refdb:
            required.append("global-refdb")
        LOG.info("Requiring libs: %s", required)
        return required

    def _install_required_plugins(self):
        for plugin in self.required_plugins:
            if plugin in self.config.get_plugin_names():
                continue

            self._install_required_jar(plugin, self.plugin_dir)

    def _install_required_libs(self):
        for lib in self.required_libs:
            if lib in self.config.get_lib_names():
                continue

            self._install_required_jar(lib, self.lib_dir)

    def _install_required_jar(self, jar, target_dir):
        with ZipFile("/var/war/gerrit.war", "r") as war:
            # Lib modules can be packaged as a plugin. However, they could
            # currently not be installed by the init pgm tool.
            if f"WEB-INF/plugins/{jar}.jar" in war.namelist():
                self._install_plugin_from_war(jar, target_dir)
                return
        try:
            self._install_jar_from_container(jar, target_dir)
        except FileNotFoundError:
            raise MissingRequiredPluginException(f"Required jar {jar} was not found.")

    def _install_jar_from_container(self, plugin, target_dir):
        source_file = os.path.join("/var/plugins", plugin + ".jar")
        target_file = os.path.join(target_dir, plugin + ".jar")
        LOG.info(
            "Installing plugin %s from container to %s.",
            plugin,
            target_file,
        )
        if not os.path.exists(source_file):
            raise FileNotFoundError(
                "Unable to find required plugin in container: " + plugin
            )
        if os.path.exists(target_file) and self._get_file_sha(
            source_file
        ) == self._get_file_sha(target_file):
            return

        shutil.copyfile(source_file, target_file)
        self.plugins_changed = True

    def _install_plugins_from_war(self):
        for plugin in self.config.get_packaged_plugins():
            self._install_plugin_from_war(plugin["name"], self.plugin_dir)

    def _install_plugin_from_war(self, plugin, target_dir):
        LOG.info("Installing packaged plugin %s.", plugin)
        with ZipFile("/var/war/gerrit.war", "r") as war:
            war.extract(f"WEB-INF/plugins/{plugin}.jar", self.plugin_dir)

        source_file = f"{self.plugin_dir}/WEB-INF/plugins/{plugin}.jar"
        target_file = os.path.join(target_dir, f"{plugin}.jar")
        if not os.path.exists(target_file) or self._get_file_sha(
            source_file
        ) != self._get_file_sha(target_file):
            os.rename(source_file, target_file)
            self.plugins_changed = True

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
        wanted_plugins = list(self.config.get_plugins())
        wanted_plugins.extend(self.required_plugins)
        self._remove_unwanted(
            wanted_plugins, self._get_installed_plugins(), self.plugin_dir
        )

    def _remove_unwanted_libs(self):
        wanted_libs = list(self.config.get_libs())
        wanted_libs.extend(self.required_libs)
        wanted_libs.extend(self.config.get_plugins_installed_as_lib())
        self._remove_unwanted(wanted_libs, self._get_installed_libs(), self.lib_dir)

    @staticmethod
    def _remove_unwanted(wanted, installed, dir):
        for plugin in installed:
            if os.path.splitext(plugin)[0] not in wanted:
                os.remove(os.path.join(dir, plugin))
                LOG.info("Removed plugin %s", plugin)

    def _symlink_plugins_to_lib(self):
        if not os.path.exists(self.lib_dir):
            os.makedirs(self.lib_dir)
        else:
            for f in os.listdir(self.lib_dir):
                path = os.path.join(self.lib_dir, f)
                if (
                    os.path.islink(path)
                    and os.path.splitext(f)[0]
                    not in self.config.get_plugins_installed_as_lib()
                ):
                    os.unlink(path)
                    LOG.info("Removed symlink %s", f)
        for lib in self.config.get_plugins_installed_as_lib():
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
        self._create_lib_dir()

        self._remove_unwanted_plugins()
        self._remove_unwanted_libs()

        self._install_required_plugins()
        self._install_required_libs()

        self._install_plugins_from_war()

        for plugin in self.config.get_downloaded_plugins():
            self._install_plugin(plugin)

        for plugin in self.config.get_libs():
            self._install_lib(plugin)

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

    def _install_plugin(self, plugin):
        self._install_jar(plugin, self.plugin_dir)

    def _install_lib(self, lib):
        self._install_jar(lib, self.lib_dir)

    @abstractmethod
    def _install_jar(self, plugin, target_dir):
        pass


class PluginInstaller(AbstractPluginInstaller):
    def _install_jar(self, plugin, target_dir):
        target = os.path.join(target_dir, f"{plugin['name']}.jar")
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
            self._create_plugin_cache_dir(os.path.dirname(cached_plugin_path))

            lock_path = f"{cached_plugin_path}.lock"
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
                self._download_plugin(plugin, cached_plugin_path)
            finally:
                self._remove_download_lock(lock_path)

        shutil.copy(cached_plugin_path, target)
        self._cleanup_cache(os.path.dirname(cached_plugin_path))

    def _install_jar(self, plugin, target_dir):
        install_path = os.path.join(target_dir, f"{plugin['name']}.jar")
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
