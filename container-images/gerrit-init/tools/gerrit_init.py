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
import os.path
import subprocess
import sys
import time

from git_config_parser import GitConfigParser
from validate_db import select_db

def ensure_database_connection(gerrit_site_path):
  db = select_db(gerrit_site_path)
  db.wait_for_db_server()

def determine_is_slave(gerrit_site_path):
  gerrit_config_path = os.path.join(gerrit_site_path, "etc/gerrit.config")
  config = GitConfigParser(gerrit_config_path)
  return config.get_boolean("container.slave", False)

def initialize_gerrit(gerrit_site_path, plugins):
  if os.path.exists(os.path.join(gerrit_site_path, "etc/gerrit.config")):
    print("%s: Existing gerrit.config found." % time.ctime())
    ensure_database_connection(gerrit_site_path)
  else:
    print("%s: No gerrit.config found. Initializing default site." % time.ctime())

  if plugins:
    plugin_options = ' '.join(['--install-plugin %s' % plugin for plugin in plugins])
  else:
    plugin_options = ''

  flags = "--no-auto-start --batch"

  if determine_is_slave(gerrit_site_path):
    flags += " --no-reindex"

  command = "java -jar /var/war/gerrit.war init %s %s -d %s" % (
    flags,
    plugin_options,
    gerrit_site_path)

  call = subprocess.run(command.split(), stdout=subprocess.PIPE)

  if call.returncode > 0:
    print("An error occured, when initializing Gerrit. Exit code: ", exit_code)
    sys.exit(1)

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
    help="Plugins to be installed. Only used, if '--initialize' is set.",
    dest="plugins",
    action="append",
    default=None)
  args = parser.parse_args()

  initialize_gerrit(args.site, args.plugins)
