#!/usr/bin/python3

# Copyright (C) 2019 The Android Open Source Project
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

from git_config_parser import GitConfigParser
from log import get_logger

LOG = get_logger("reindex")


class GerritReindexer:
    def __init__(self, gerrit_site_path):
        self.gerrit_site_path = gerrit_site_path
        self.index_config_path = "%s/index/gerrit_index.config" % self.gerrit_site_path

        self.index_type = self._get_index_type()
        self.configured_indices = self._parse_gerrit_index_config()

    def _get_index_type(self):
        gerrit_config = GitConfigParser(
            os.path.join(self.gerrit_site_path, "etc", "gerrit.config")
        )
        return gerrit_config.get("index.type", "lucene").lower()

    def _parse_gerrit_index_config(self):
        indices = dict()
        if os.path.exists(self.index_config_path):
            config = GitConfigParser(self.index_config_path)
            options = config.list()
            for opt in options:
                name, version = opt["subsection"].rsplit("_", 1)
                # TODO (Thomas): Properly handle multiple versions of the same index,
                # which may be present due to online-reindexing in progress.
                indices[name] = {
                    "version": int(version),
                    "ready": opt["value"].lower() == "true",
                }
        return indices

    def _get_unready_indices(self):
        unready_indices = []
        for index, index_attrs in self.configured_indices.items():
            if not index_attrs["ready"]:
                LOG.info("Index %s not ready.", index)
                unready_indices.append(index)
        return unready_indices

    def _get_lucene_indices(self):
        file_list = os.listdir(os.path.join(self.gerrit_site_path, "index"))
        file_list.remove("gerrit_index.config")
        lucene_indices = dict()
        for index in file_list:
            try:
                (name, version) = index.split("_")
                lucene_indices[name] = int(version)
            except ValueError:
                LOG.debug("Ignoring invalid file in index-directory: %s", index)
        return lucene_indices

    def _check_lucene_index_versions(self):
        lucene_indices = self._get_lucene_indices()
        if not lucene_indices:
            return False
        for index, index_attrs in self.configured_indices.items():
            if index_attrs["version"] is not lucene_indices[index]:
                return False
        return True

    def reindex(self, indices=None):
        LOG.info("Starting to reindex.")
        command = "java -jar /var/war/gerrit.war reindex -d %s" % self.gerrit_site_path

        if indices:
            command += " ".join([" --index %s" % i for i in indices])

        reindex_process = subprocess.run(command.split(), stdout=subprocess.PIPE)

        if reindex_process.returncode > 0:
            LOG.error(
                "An error occured, when reindexing Gerrit indices. Exit code: %d",
                reindex_process.returncode,
            )
            sys.exit(1)

        LOG.info("Finished reindexing.")

    def start(self, is_forced):
        if is_forced:
            self.reindex()
            return

        if not self.configured_indices:
            LOG.info("gerrit_index.config does not exist. Creating all indices.")
            self.reindex()
            return

        unready_indices = self._get_unready_indices()
        if unready_indices:
            self.reindex(unready_indices)

        if self.index_type == "lucene":
            if not self._check_lucene_index_versions():
                LOG.info("Not all indices are up-to-date.")
                self.reindex()
                return
        else:
            self.reindex()
            return

        LOG.info("Skipping reindexing.")


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
        "-f",
        "--force",
        help="Reindex even if indices are ready.",
        dest="force",
        action="store_true",
    )
    args = parser.parse_args()

    reindexer = GerritReindexer(args.site)
    reindexer.start(args.force)
