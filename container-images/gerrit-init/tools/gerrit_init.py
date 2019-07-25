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

from git_config_parser import GitConfigParser
from log import get_logger
from validate_db import select_db

LOG = get_logger("init")


class GerritInit:
    def __init__(self, site, wanted_plugins, enable_reviewdb):
        self.site = site
        self.wanted_plugins = set(wanted_plugins)
        self.enable_reviewdb = enable_reviewdb

        self.gerrit_config = self._parse_gerrit_config()
        self.is_slave = self._is_slave()
        self.installed_plugins = self._get_installed_plugins()

    def _parse_gerrit_config(self):
        gerrit_config_path = os.path.join(self.site, "etc/gerrit.config")

        if os.path.exists(gerrit_config_path):
            return GitConfigParser(gerrit_config_path)

        return None

    def _ensure_database_connection(self):
        database = select_db(self.site)
        database.wait_for_db_server()

    def _is_slave(self):
        if self.gerrit_config:
            return self.gerrit_config.get_boolean("container.slave", False)

        return False

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

    def _needs_init(self):
        init_lock_path = os.path.join(self.site, "init.lock")
        if os.path.exists(init_lock_path):
            LOG.info("Found %s. Initializing.", init_lock_path)
            return True

        installed_war_path = os.path.join(self.site, "bin", "gerrit.war")
        if not os.path.exists(installed_war_path):
            LOG.info("Gerrit is not yet installed. Initializing new site.")
            return True

        installed_version = self._get_gerrit_version(installed_war_path)
        provided_version = self._get_gerrit_version("/var/war/gerrit.war")
        if installed_version != provided_version:
            LOG.info(
                "New Gerrit version was provided (current: %s; new: %s). "
                "Reinitializing site.",
                installed_version,
                provided_version,
            )
            return True

        if self.wanted_plugins.difference(self.installed_plugins):
            LOG.info("Reininitializing site to install additional plugins.")
            return True

        LOG.info("No initialization required.")
        return False

    def execute(self):
        if not self._needs_init():
            return

        if self.gerrit_config:
            LOG.info("Existing gerrit.config found.")
            if self.enable_reviewdb:
                self._ensure_database_connection()
        else:
            LOG.info("No gerrit.config found. Initializing default site.")

        if self.wanted_plugins:
            plugin_options = " ".join(
                ["--install-plugin %s" % plugin for plugin in self.wanted_plugins]
            )
        else:
            plugin_options = ""

        flags = "--no-auto-start --batch"

        if self.is_slave:
            flags += " --no-reindex"

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

        init_lock_path = os.path.join(self.site, "init.lock")
        if os.path.exists(init_lock_path):
            os.remove(init_lock_path)


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
        "-p",
        "--plugin",
        help="Gerrit plugin to be installed from war-file. Can be used multiple times.",
        dest="wanted_plugins",
        action="append",
        default=list(),
    )
    parser.add_argument(
        "-d",
        "--reviewdb",
        help="Whether a reviewdb is part of the Gerrit installation.",
        dest="reviewdb",
        action="store_true",
    )
    args = parser.parse_args()

    init = GerritInit(args.site, args.wanted_plugins, args.reviewdb)
    init.execute()
