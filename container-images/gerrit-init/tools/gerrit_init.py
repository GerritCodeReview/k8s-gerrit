#!/usr/bin/python3

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

import argparse
import os
import subprocess
import sys

from download_plugins import get_installer
from git_config_parser import GitConfigParser
from init_config import InitConfig
from log import get_logger

LOG = get_logger("init")


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
            return GitConfigParser(gerrit_config_path)

        return None

    def _get_gerrit_version(self, gerrit_war_path):
        command = "java -jar %s version" % gerrit_war_path
        version_process = subprocess.run(command.split(), stdout=subprocess.PIPE)
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
        else:
            LOG.info("No gerrit.config found. Initializing default site.")

        if self.config.packaged_plugins:
            plugin_options = " ".join(
                [
                    "--install-plugin %s" % plugin
                    for plugin in self.config.packaged_plugins
                    if plugin
                ]
            )
        else:
            plugin_options = ""

        flags = "--no-auto-start --batch"

        command = "java -jar /var/war/gerrit.war init %s %s -d %s" % (
            flags,
            plugin_options,
            self.site,
        )

        init_process = subprocess.run(command.split(), stdout=subprocess.PIPE)

        if init_process.returncode > 0:
            LOG.error(
                "An error occured, when initializing Gerrit. Exit code: %d",
                init_process.returncode,
            )
            sys.exit(1)


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

    init = GerritInit(args.site, config)
    init.execute()
