# Copyright (C) 2024 The Android Open Source Project
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

import os

from ..helpers import log, git, git_config_witer

LOG = log.get_logger("init")
MNT_PATH = "/var/mnt"


class PullReplicationConfigurator:
    def __init__(self, site, config):
        self.site = site
        self.config = config
        self.pod_name = os.environ.get("POD_NAME")
        self.pod_id = self._get_pod_identifier()
        self.pod_name_prefix = self._get_pod_name_prefix()

    @staticmethod
    def has_pull_replication():
        return os.path.exists(
            os.path.join(MNT_PATH, "etc/config/replication.config")
        ) and (
            os.path.exists("/var/gerrit/lib/pull-replication.jar")
            or os.path.exists("/var/gerrit/plugins/pull-replication.jar")
        )

    def _get_pod_identifier(self):
        # POD_INDEX is available in K8s only from version v1.28,
        # so we retrieve it from POD_NAME that is already exposed as env var
        return int(self.pod_name.rsplit("-", 1)[1])

    def _get_pod_name_prefix(self):
        return self.pod_name.rsplit("-", 1)[0]

    def _configure_remotes(self, template_path):
        config = git.GitConfigParser(template_path)
        config_writer = git_config_witer.GitConfigWriter(config.list())

        config_writer.remove_subsection(
            "remote", f"{self.pod_name_prefix}-{self.pod_id}"
        )
        config_writer.write_config(os.path.join(self.site, "etc/replication.config"))
        LOG.info(
            f'Set pull replication for remote "{self.pod_name_prefix}-{self.pod_id}"'
        )

    def configure(self):
        LOG.info(f"Setting pull replication configuration for pod-idx: {self.pod_id}")
        replication_config_configmap = os.path.join(
            MNT_PATH, "etc/config/replication.config"
        )
        self._configure_remotes(replication_config_configmap)
