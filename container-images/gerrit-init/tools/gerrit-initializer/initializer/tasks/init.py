# Copyright (C) 2018 The Android Open Source Project
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

import os
import shutil
import subprocess
import sys

from abc import abstractmethod
from ..helpers import git, log
from .download_plugins import get_installer
from .pull_replication_configurator import PullReplicationConfigurator
from .reindex import get_reindexer
from .validate_notedb import NoteDbValidator

LOG = log.get_logger("init")
MNT_PATH = "/var/mnt"


class GerritInit:
    def __init__(self, site, config):
        self.site = site
        self.config = config

        self.plugin_installer = get_installer(self.site, self.config)

        self.gerrit_config = git.GitConfigParser(
            os.path.join(MNT_PATH, "etc/config/gerrit.config")
        )
        self.installed_plugins = self._get_installed_plugins()

        self.is_replica = self.gerrit_config.get_boolean("container.replica")
        self.pid_file = f"{self.site}/logs/gerrit.pid"

    @abstractmethod
    def _symlink_mounted_site_components(self):
        pass

    @abstractmethod
    def _symlink_configuration(self):
        pass

    def _get_gerrit_version(self, gerrit_war_path):
        command = f"java -jar {gerrit_war_path} version"
        version_process = subprocess.run(
            command.split(), stdout=subprocess.PIPE, check=True
        )
        return version_process.stdout.decode().strip()

    def _get_installed_plugins(self):
        plugin_path = os.path.join(self.site, "plugins")
        installed_plugins = set()

        if os.path.exists(plugin_path):
            for f in os.listdir(plugin_path):
                if os.path.isfile(os.path.join(plugin_path, f)) and f.endswith(".jar"):
                    installed_plugins.add(os.path.splitext(f)[0])

        return installed_plugins

    def _gerrit_war_updated(self):
        installed_war_path = os.path.join(self.site, "bin", "gerrit.war")
        installed_version = self._get_gerrit_version(installed_war_path)
        provided_version = self._get_gerrit_version("/var/war/gerrit.war")
        LOG.info(
            "Installed Gerrit version: %s; Provided Gerrit version: %s). ",
            installed_version,
            provided_version,
        )

        return installed_version != provided_version

    def _needs_init(self):
        installed_war_path = os.path.join(self.site, "bin", "gerrit.war")
        if not os.path.exists(installed_war_path):
            LOG.info("Gerrit is not yet installed. Initializing new site.")
            return True

        if self._gerrit_war_updated():
            LOG.info("Reinitializing site to perform update.")
            return True

        if self.plugin_installer.plugins_changed:
            LOG.info("Plugins were installed or updated. Initializing.")
            return True

        if self.config.get_plugin_names().difference(self.installed_plugins):
            LOG.info("Reininitializing site to install additional plugins.")
            return True

        LOG.info("No initialization required.")
        return False

    def _symlink(self, src, target, required=True):
        if os.path.islink(target) and not os.path.exists(os.readlink(target)):
            LOG.warn(f"Removing broken symlink {target}")
            os.unlink(target)

        if not os.path.exists(src):
            if required:
                raise FileNotFoundError(f"Unable to find mounted dir: {src}")
            else:
                return

        if os.path.exists(target):
            if os.path.isdir(target) and not os.path.islink(target):
                shutil.rmtree(target)
            else:
                os.remove(target)

        os.symlink(src, target)

    def _symlink_or_make_data_dir(self):
        data_dir = f"{self.site}/data"
        if os.path.exists(data_dir):
            for file_or_dir in os.listdir(data_dir):
                abs_path = os.path.join(data_dir, file_or_dir)
                if os.path.islink(abs_path) and not os.path.exists(
                    os.path.realpath(abs_path)
                ):
                    os.unlink(abs_path)
        else:
            os.makedirs(data_dir)

        mounted_data_dir = f"{MNT_PATH}/data"
        if os.path.exists(mounted_data_dir):
            for file_or_dir in os.listdir(mounted_data_dir):
                abs_path = os.path.join(data_dir, file_or_dir)
                abs_mounted_path = os.path.join(mounted_data_dir, file_or_dir)
                if os.path.isdir(abs_mounted_path):
                    self._symlink(abs_mounted_path, abs_path)

    def _remove_auto_generated_ssh_keys(self):
        etc_dir = f"{self.site}/etc"
        if not os.path.exists(etc_dir):
            return

        for file_or_dir in os.listdir(etc_dir):
            full_path = os.path.join(etc_dir, file_or_dir)
            if os.path.isfile(full_path) and file_or_dir.startswith("ssh_host_"):
                os.remove(full_path)

    def execute(self):
        # Required for migration away from NFS-based log storage, when using the
        # Gerrit-Operator and to provide backwards compatibility for the helm-
        # charts
        self._symlink(f"{MNT_PATH}/logs", f"{self.site}/logs", required=False)

        if not self.is_replica:
            self._symlink_mounted_site_components()
        elif not NoteDbValidator(MNT_PATH).check():
            LOG.info("NoteDB not ready. Initializing repositories.")
            self._symlink_mounted_site_components()
        self._symlink_configuration()

        if os.path.exists(self.pid_file):
            os.remove(self.pid_file)

        self.plugin_installer.execute()
        self._symlink_configuration()

        if PullReplicationConfigurator.has_pull_replication():
            PullReplicationConfigurator(
                self.site, self.config
            ).configure_pull_replication()

        if self._needs_init():
            if self.gerrit_config:
                LOG.info("Existing gerrit.config found.")
                dev_option = (
                    "--dev"
                    if self.gerrit_config.get(
                        "auth.type", "development_become_any_account"
                    ).lower()
                    == "development_become_any_account"
                    else ""
                )
            else:
                LOG.info("No gerrit.config found. Initializing default site.")
                dev_option = "--dev"

            flags = f"--no-auto-start --batch {dev_option}"

            command = f"java -jar /var/war/gerrit.war init {flags} -d {self.site}"

            init_process = subprocess.run(
                command.split(), stdout=subprocess.PIPE, check=True
            )

            if init_process.returncode > 0:
                LOG.error(
                    "An error occurred, when initializing Gerrit. Exit code: %d",
                    init_process.returncode,
                )
                sys.exit(1)

            self._remove_auto_generated_ssh_keys()
            self._symlink_configuration()

            if PullReplicationConfigurator.has_pull_replication():
                PullReplicationConfigurator(
                    self.site, self.config
                ).configure_gerrit_configuration()

            if self.is_replica:
                self._symlink_mounted_site_components()

        get_reindexer(self.site, self.config).start(False)
