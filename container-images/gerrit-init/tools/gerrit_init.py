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
import logging
import os
import subprocess
import sys
import time

from git_config_parser import GitConfigParser
from validate_db import select_db

LOG = logging.Logger("init")
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter('[%(asctime)s] %(levelname)s %(message)s'))
LOG.addHandler(handler)
LOG.setLevel(logging.DEBUG)

def ensure_database_connection(gerrit_site_path):
  database = select_db(gerrit_site_path)
  database.wait_for_db_server()

def determine_is_slave(gerrit_site_path):
  gerrit_config_path = os.path.join(gerrit_site_path, "etc/gerrit.config")
  config = GitConfigParser(gerrit_config_path)
  return config.get_boolean("container.slave", False)

def get_gerrit_version(gerrit_war_path):
  command = "java -jar %s version" % gerrit_war_path
  version_process = subprocess.run(
    command.split(),
    stdout=subprocess.PIPE)
  return version_process.stdout.decode().strip()

def get_installed_plugins(gerrit_site_path):
  plugin_path = os.path.join(gerrit_site_path, "plugins")
  installed_plugins = set()

  if os.path.exists(plugin_path):
    for f in os.listdir(plugin_path):
      if os.path.isfile(os.path.join(plugin_path, f)) and f.endswith(".jar"):
        installed_plugins.add(os.path.splitext(f)[0])

  return installed_plugins

def remove_unwanted_plugins(gerrit_site_path, wanted_plugins):
  installed_plugins = get_installed_plugins(gerrit_site_path)
  for plugin in installed_plugins.difference(wanted_plugins):
    LOG.info("Removing plugin %s" % plugin)
    os.remove(os.path.join(gerrit_site_path, "plugins", "%s.jar" % plugin))

def needs_init(gerrit_site_path, wanted_plugins):
  installed_war_path = os.path.join(gerrit_site_path, "bin", "gerrit.war")
  if not os.path.exists(installed_war_path):
    LOG.info("Gerrit is not yet installed. Initializing new site.")
    return True

  installed_version = get_gerrit_version(installed_war_path)
  provided_version = get_gerrit_version("/var/war/gerrit.war")
  if installed_version != provided_version:
    LOG.info("New Gerrit version was provided (current: %s; new: %s). "
      "Reinitializing site.") % (
        installed_version,
        provided_version))
    return True

  installed_plugins = get_installed_plugins(gerrit_site_path)
  if wanted_plugins.difference(installed_plugins):
    LOG.info("Reininitializing site to install additional plugins.")
    return True

  LOG.info("No initialization required.")
  return False

def initialize_gerrit(gerrit_site_path, wanted_plugins):
  remove_unwanted_plugins(gerrit_site_path, wanted_plugins)

  if not needs_init(gerrit_site_path, wanted_plugins):
    return

  if os.path.exists(os.path.join(gerrit_site_path, "etc/gerrit.config")):
    LOG.info("Existing gerrit.config found.")
    ensure_database_connection(gerrit_site_path)
  else:
    LOG.info("No gerrit.config found. Initializing default site.")

  if wanted_plugins:
    plugin_options = ' '.join(['--install-plugin %s' % plugin for plugin in wanted_plugins])
  else:
    plugin_options = ''

  flags = "--no-auto-start --batch"

  if determine_is_slave(gerrit_site_path):
    flags += " --no-reindex"

  command = "java -jar /var/war/gerrit.war init %s %s -d %s" % (
    flags,
    plugin_options,
    gerrit_site_path)

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
    default=list())
  args = parser.parse_args()

  initialize_gerrit(args.site, set(args.wanted_plugins))
