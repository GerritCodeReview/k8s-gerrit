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

def reindex(gerrit_site_path):
  print("%s: Starting to reindex." % time.ctime())
  command = "java -jar /var/war/gerrit.war reindex -d %s" % (gerrit_site_path)
  reindex_process = subprocess.run(command.split(), stdout=subprocess.PIPE)

  if reindex_process.returncode > 0:
    print("An error occured, when reindexing Gerrit indices. Exit code: ",
          reindex_process.returncode)
    sys.exit(1)

  print("%s: Finished reindexing." % time.ctime())

def all_indices_ready(index_config_path):
  config = GitConfigParser(index_config_path)
  indices = config.list()
  for index in indices:
    if index[1].lower() == "false":
      return False
  return True

def schedule_reindex(gerrit_site_path, is_forced):
  if is_forced:
    reindex(gerrit_site_path)
    return

  indices_ready = False
  index_config_path = "%s/index/gerrit_index.config" % gerrit_site_path
  if os.path.exists(index_config_path):
    indices_ready = all_indices_ready(index_config_path)

  if not indices_ready:
    print("%s: Not all indices are ready." % time.ctime())
    reindex(gerrit_site_path)
    return

  print("%s: Skipping reindexing." % time.ctime())


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
    "-f",
    "--force",
    help="Reindex even if indices are ready.",
    dest="force",
    action="store_true")
  args = parser.parse_args()

  schedule_reindex(args.site, args.force)
