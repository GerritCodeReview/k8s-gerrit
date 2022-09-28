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
import subprocess
import sys

from ..helpers import git, log
from .download_plugins import get_installer

LOG = log.get_logger("init")


class GerritInit:
    def __init__(self, site, config):
        self.site = site
        self.config = config

        self.plugin_installer = get_installer(self.site, self.config)

        self.gerrit_config = self._parse_gerrit_config()
        self.installed_plugins = self._get_installed_plugins()

    def _parse_gerrit_config(self):
        gerrit_config_path = os.path.join(self.site, "etc/gerrit.config")

        if os.path.exists(gerrit_config_path):
            return git.GitConfigParser(gerrit_config_path)

        return None

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
        if self.plugin_installer.plugins_changed:
            LOG.info("Plugins were installed or updated. Initializing.")
            return True

        installed_war_path = os.path.join(self.site, "bin", "gerrit.war")
        if not os.path.exists(installed_war_path):
            LOG.info("Gerrit is not yet installed. Initializing new site.")
            return True

        if self._gerrit_war_updated():
            LOG.info("Reinitializing site to perform update.")
            return True

        if self.config.packaged_plugins.difference(self.installed_plugins):
            LOG.info("Reininitializing site to install additional plugins.")
            return True

        LOG.info("No initialization required.")
        return False

    def execute(self):
        self.plugin_installer.execute()

        if not self._needs_init():
            return

        if self.gerrit_config:
            LOG.info("Existing gerrit.config found.")
            dev_option = (
                "--dev"
                if self.gerrit_config.get("auth.type").lower()
                == "development_become_any_account"
                else ""
            )
        else:
            LOG.info("No gerrit.config found. Initializing default site.")
            dev_option = "--dev"

        flags = "--no-auto-start --batch {dev_option}"

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
