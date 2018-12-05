#!/usr/bin/python3

import argparse
import os.path
import subprocess
import sys
import time

from validate_db import select_db
from read_gerrit_config import read_gerrit_config

def ensure_database_connection(gerrit_site_path):
  db = select_db(gerrit_site_path)
  db.wait_for_db_server()

def determine_is_slave(gerrit_site_path):
  config = read_gerrit_config(gerrit_site_path)
  return config.getboolean("container", "slave", fallback=False)

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

  exit_code = subprocess.call(command.split(), stdout=subprocess.PIPE)

  if exit_code > 0:
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

  try:
    gerrit_site_path = args.site
    plugins = args.plugins
  except Exception as e:
    print("No Gerrit site specified.")
    sys.exit(1)

  initialize_gerrit(gerrit_site_path, plugins)
