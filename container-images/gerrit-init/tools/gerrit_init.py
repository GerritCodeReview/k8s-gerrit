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
import time

from git_config_parser import GitConfigParser
from validate_db import select_db

class GerritInit():

  def __init__(self, site):
    self.site = site

    self.gerrit_config = self._parse_gerrit_config()
    self.is_slave = self._is_slave()

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
    version_process = subprocess.run(
      command.split(),
      stdout=subprocess.PIPE)
    return version_process.stdout.decode().strip()

  def _get_installed_plugins(self):
    plugin_path = os.path.join(self.site, "plugins")
    installed_plugins = set()

    for f in os.listdir(plugin_path):
      if os.path.isfile(os.path.join(plugin_path, f)) and f.endswith(".jar"):
        installed_plugins.add(os.path.splitext(f)[0])

    return installed_plugins

  def _needs_init(self, wanted_plugins):
    installed_war_path = os.path.join(self.site, "bin", "gerrit.war")
    if not os.path.exists(installed_war_path):
      print("%s: Gerrit is not yet installed. Initializing new site." % time.ctime())
      return True

    installed_version = self._get_gerrit_version(installed_war_path)
    provided_version = self._get_gerrit_version("/var/war/gerrit.war")
    if installed_version != provided_version:
      print((
        "%s: New Gerrit version was provided (current: %s; new: %s). "
        "Reinitializing site.") % (
          time.ctime(),
          installed_version,
          provided_version))
      return True

    installed_plugins = self._get_installed_plugins()
    if installed_plugins.symmetric_difference(wanted_plugins):
      for plugin in installed_plugins.difference(wanted_plugins):
        os.remove(os.path.join(self.site, "plugins", "%s.jar" % plugin))
      return True

    print("%s: No initialization required." % time.ctime())
    return False

  def start(self, wanted_plugins):
    if not self._needs_init(wanted_plugins):
      return

    if self.gerrit_config:
      print("%s: Existing gerrit.config found." % time.ctime())
      self._ensure_database_connection()
    else:
      print("%s: No gerrit.config found. Initializing default site." % time.ctime())

    if wanted_plugins:
      plugin_options = ' '.join(['--install-plugin %s' % plugin for plugin in wanted_plugins])
    else:
      plugin_options = ''

    flags = "--no-auto-start --batch"

    if self.is_slave:
      flags += " --no-reindex"

    command = "java -jar /var/war/gerrit.war init %s %s -d %s" % (
      flags,
      plugin_options,
      self.site)

    init_process = subprocess.run(command.split(), stdout=subprocess.PIPE)

    if init_process.returncode > 0:
      print("An error occured, when initializing Gerrit. Exit code: ",
            init_process.returncode)
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
    required=True)
  parser.add_argument(
    "-p",
    "--plugin",
    help="Gerrit plugin to be installed. Can be used multiple times.",
    dest="wanted_plugins",
    action="append",
    default=None)
  args = parser.parse_args()

  init = GerritInit(args.site)
  init.start(args.wanted_plugins)
