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

import abc
import os.path
import shutil
import subprocess
import sys

import requests

from ..helpers import git, log

LOG = log.get_logger("reindex")
MNT_PATH = "/var/mnt"
INDEXES_PRIMARY = set(["accounts", "changes", "groups", "projects"])
INDEXES_REPLICA = set(["groups"])


class GerritAbstractReindexer(abc.ABC):
    def __init__(self, gerrit_site_path, config):
        self.gerrit_site_path = gerrit_site_path
        self.index_config_path = f"{self.gerrit_site_path}/index/gerrit_index.config"
        self.init_config = config

        self.gerrit_config = git.GitConfigParser(
            os.path.join(MNT_PATH, "etc/config/gerrit.config")
        )
        self.is_replica = self.gerrit_config.get_boolean("container.replica", False)

        self.configured_indices = self._parse_gerrit_index_config()

    @abc.abstractmethod
    def _get_indices(self):
        pass

    @abc.abstractmethod
    def _prepare(self):
        pass

    def _parse_gerrit_index_config(self):
        indices = {}
        if os.path.exists(self.index_config_path):
            config = git.GitConfigParser(self.index_config_path)
            options = config.list()
            for opt in options:
                name, version = opt["subsection"].rsplit("_", 1)
                ready = opt["value"].lower() == "true"
                if name in indices:
                    indices[name] = {
                        "read": version if ready else indices[name]["read"],
                        "latest_write": max(version, indices[name]["latest_write"]),
                    }
                else:
                    indices[name] = {
                        "read": version if ready else None,
                        "latest_write": version,
                    }
        return indices

    def _get_not_ready_indices(self):
        not_ready_indices = []
        for index, index_attrs in self.configured_indices.items():
            if not index_attrs["read"]:
                LOG.info("Index %s not ready.", index)
                not_ready_indices.append(index)
        index_set = INDEXES_REPLICA if self.is_replica else INDEXES_PRIMARY
        not_ready_indices.extend(index_set.difference(self.configured_indices.keys()))
        return not_ready_indices

    def _get_missing_indices(self):
        indices = self._get_indices()

        missing_indices = []
        index_set = INDEXES_REPLICA if self.is_replica else INDEXES_PRIMARY
        if not indices:
            return index_set

        for index in index_set:
            if index not in indices:
                missing_indices.append(index)
        return missing_indices

    def reindex(self, indices=None):
        LOG.info("Starting to reindex.")
        command = f"java -jar /var/war/gerrit.war reindex -d {self.gerrit_site_path}"

        if indices:
            command += " ".join([f" --index {i}" for i in indices])

        reindex_process = subprocess.run(
            command.split(), stdout=subprocess.PIPE, check=True
        )

        if reindex_process.returncode > 0:
            LOG.error(
                "An error occurred, when reindexing Gerrit indices. Exit code: %d",
                reindex_process.returncode,
            )
            sys.exit(1)

        LOG.info("Finished reindexing.")

    def start(self, is_forced):
        self._prepare()
        self.configured_indices = self._parse_gerrit_index_config()

        if is_forced:
            self.reindex()
            return

        if not self.configured_indices:
            LOG.info("gerrit_index.config does not exist. Creating all indices.")
            self.reindex()
            return

        missing_indices = self._get_missing_indices()
        if missing_indices:
            LOG.info("Missing at least one index. Reindexing all.")
            self.reindex(missing_indices)
            return

        not_ready_indices = self._get_not_ready_indices()
        if not_ready_indices:
            self.reindex(not_ready_indices)
            return

        LOG.info("Skipping reindexing.")


class GerritLuceneReindexer(GerritAbstractReindexer):
    def _prepare(self):
        pass

    def _get_indices(self):
        file_list = os.listdir(os.path.join(self.gerrit_site_path, "index"))
        file_list.remove("gerrit_index.config")
        lucene_indices = {}
        for index in file_list:
            try:
                (name, version) = index.split("_")
                if name in lucene_indices:
                    lucene_indices[name] = max(version, lucene_indices[name])
                else:
                    lucene_indices[name] = version
            except ValueError:
                LOG.debug("Ignoring invalid file in index-directory: %s", index)
        return lucene_indices


class GerritElasticSearchReindexer(GerritAbstractReindexer):
    def _get_elasticsearch_config(self):
        es_config = {}
        gerrit_config = git.GitConfigParser(
            os.path.join(self.gerrit_site_path, "etc", "gerrit.config")
        )
        es_config["prefix"] = gerrit_config.get(
            "elasticsearch.prefix", default=""
        ).lower()
        es_config["server"] = gerrit_config.get(
            "elasticsearch.server", default=""
        ).lower()
        return es_config

    def _get_indices(self):
        es_config = self._get_elasticsearch_config()
        url = f"{es_config['server']}/{es_config['prefix']}*"
        try:
            response = requests.get(url)
        except requests.exceptions.SSLError:
            response = requests.get(url, verify=self.init_config.ca_cert_path)

        es_indices = {}
        for index, _ in response.json().items():
            try:
                index = index.replace(es_config["prefix"], "", 1)
                (name, version) = index.split("_")
                es_indices[name] = version
            except ValueError:
                LOG.debug("Found unknown index: %s", index)

        return es_indices

    def _prepare(self):
        index_mnt_path = f"{MNT_PATH}/index"
        index_site_path = f"{self.gerrit_site_path}/index"
        if os.path.exists(index_site_path):
            if os.path.islink(index_site_path):
                os.unlink(index_site_path)
            elif os.path.isdir(index_site_path):
                shutil.rmtree(index_site_path)
        LOG.info(os.path.exists(index_site_path))
        os.symlink(index_mnt_path, index_site_path, target_is_directory=True)


def get_reindexer(gerrit_site_path, config):
    gerrit_config = git.GitConfigParser(
        os.path.join(gerrit_site_path, "etc", "gerrit.config")
    )

    indexModule = gerrit_config.get("gerrit.installIndexModule")
    if indexModule and indexModule.startswith("com.google.gerrit.elasticsearch"):
        return GerritElasticSearchReindexer(gerrit_site_path, config)

    return GerritLuceneReindexer(gerrit_site_path, config)
