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
import re
import subprocess
import sys
import time

from git_config_parser import GitConfigParser

class GerritReindexer:

  def __init__(self, gerrit_site_path):
    self.gerrit_site_path = gerrit_site_path
    self.index_config_path = "%s/index/gerrit_index.config" % self.gerrit_site_path

    self.index_type = self._get_index_type()
    self.configured_indices = self._parse_gerrit_index_config()

  def _get_index_type(self):
    gerrit_config = GitConfigParser(
      os.path.join(self.gerrit_site_path, "etc", "gerrit.config"))
    return gerrit_config.get("index.type").lower()

  def _parse_gerrit_index_config(self):
    indices = dict()
    if os.path.exists(self.index_config_path):
      config = GitConfigParser(self.index_config_path)
      options = config.list()
      for opt in options:
        match = re.match(r"^(?P<name>.+)_(?P<version>\d+)$", opt["subsection"])
        indices[match.group("name")] = {
          "version": int(match.group("version")),
          "ready": opt["value"].lower() == "true"
        }
    return indices

  def _all_indices_ready(self):
    for _, index_attrs in self.configured_indices.items():
      if not index_attrs["ready"]:
        return False
    return True

  def _get_lucene_indices(self):
    file_list = os.listdir(os.path.join(self.gerrit_site_path, "index"))
    file_list.remove("gerrit_index.config")
    lucene_indices = dict()
    for index in file_list:
      match = re.match(r"^(?P<name>.+)_(?P<version>\d+)$", index)
      lucene_indices[match.group("name")] = int(match.group("version"))
    return lucene_indices

  def _check_lucene_index_versions(self):
    lucene_indices = self._get_lucene_indices()
    if not lucene_indices:
      return False
    for index, index_attrs in self.configured_indices.items():
      if index_attrs["version"] is not lucene_indices[index]:
        return False
    return True

  def reindex(self):
    print("%s: Starting to reindex." % time.ctime())
    command = "java -jar /var/war/gerrit.war reindex -d %s" % (self.gerrit_site_path)
    reindex_process = subprocess.run(command.split(), stdout=subprocess.PIPE)

    if reindex_process.returncode > 0:
      print("An error occured, when reindexing Gerrit indices. Exit code: ",
            reindex_process.returncode)
      sys.exit(1)

    print("%s: Finished reindexing." % time.ctime())

  def start(self, is_forced):
    if is_forced:
      self.reindex()
      return

    indices_ready = False
    if self.configured_indices:
      indices_ready = self._all_indices_ready()

    if not indices_ready:
      print("%s: Not all indices are ready." % time.ctime())
      self.reindex()
      return

    if self.index_type == "lucene":
      if not self._check_lucene_index_versions():
        print("%s: Not all indices are up-to-date." % time.ctime())
        self.reindex()
        return
    else:
      self.reindex()
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

  reindexer = GerritReindexer(args.site)
  reindexer.start(args.force)
