#!/usr/bin/python3

import argparse
import os.path
import sys
import time

from multioptconfigparser import *

def get_secure_config_path(gerrit_site_path):
  return gerrit_site_path + "/etc/secure.config"

def get_config_path(gerrit_site_path):
  return gerrit_site_path + "/etc/gerrit.config"

def read_gerrit_config(gerrit_site_path, secure=False):
  config_paths = [get_config_path(gerrit_site_path)]
  if secure:
    config_paths.append(get_secure_config_path(gerrit_site_path))
  gerrit_config = ConfigParserMultiOpt()
  gerrit_config.read(config_paths)
  return gerrit_config

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
    "-a",
    "--all",
    help="Whether the secure.config should also be parsed.",
    dest="secure",
    action="store",
    default=False)
  args = parser.parse_args()

  try:
    gerrit_site_path = args.site
    secure = args.secure
  except Exception as e:
    print("No Gerrit site specified.")
    sys.exit(1)

  config = read_gerrit_config(gerrit_site_path, secure)
