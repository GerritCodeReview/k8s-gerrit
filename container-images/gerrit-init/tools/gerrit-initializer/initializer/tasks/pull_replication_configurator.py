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

from ..helpers import log

LOG = log.get_logger("init")
MNT_PATH = "/var/mnt"
INSTANCE_ID_PLACEHOLDER = "INSTANCE_ID_PLACEHOLDER"
REMOTE_ID_PLACEHOLDER = "REMOTE_INSTANCE_ID_PLACEHOLDER"
REMOTE_HOST_PLACEHOLDER = "REMOTE_INSTANCE_HOST_PLACEHOLDER"


class PullReplicationConfigurator:
    def __init__(self, site, config):
        self.site = site
        self.config = config
        self.pod_id = self._get_pod_identifier()
        self.replicas = self._get_replicas_num()

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
        return int(os.environ.get("POD_NAME")[7:])

    def _get_replicas_num(self):
        return int(os.environ.get("REPLICAS"))

    def _configure_instance_id(self, template_path):
        with open(template_path, "r") as file:
            content = file.read()
        content = content.replace(f"{INSTANCE_ID_PLACEHOLDER}", f"gerrit-{self.pod_id}")
        with open(os.path.join(self.site, "etc/gerrit.config"), "w") as file:
            file.write(content)

    def _configure_remotes(self, template_path):
        with open(template_path, "r") as file:
            content = file.read()
        remote_pod_ids = list(range(0, self.replicas))
        del remote_pod_ids[self.pod_id]
        for idx, x in enumerate(remote_pod_ids):
            content = content.replace(
                f"{REMOTE_ID_PLACEHOLDER}-{idx + 1}", f"gerrit-{x}"
            )
            content = content.replace(
                f"{REMOTE_HOST_PLACEHOLDER}-{idx + 1}",
                f"gerrit-{x}.{self.config.gerrit_headless_service_host}",
            )
            LOG.info('Set pull replication for remote "{}"'.format(f"gerrit-{x}"))
        with open(os.path.join(self.site, "etc/replication.config"), "w") as file:
            file.write(content)

    def configure(self):
        LOG.info(
            "Setting pull replication configuration for pod-idx: {}".format(self.pod_id)
        )

        replication_config_configmap = os.path.join(
            MNT_PATH, "etc/config/replication.config"
        )
        gerrit_config_configmap = os.path.join(MNT_PATH, "etc/config/gerrit.config")

        self._configure_remotes(replication_config_configmap)
        self._configure_instance_id(gerrit_config_configmap)
