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

from ..constants import MNT_PATH, SITE_PATH
from ..helpers import log
from .init import GerritInit


LOG = log.get_logger("init")


class GerritInitMultisite(GerritInit):
    def __init__(self, gerrit_config, config):
        super().__init__(gerrit_config, config)

    def _symlink_mounted_site_components(self):
        self._symlink_or_make_data_dir()

    def _symlink_configuration(self):
        etc_dir = f"{SITE_PATH}/etc"
        if not os.path.exists(etc_dir):
            os.makedirs(etc_dir)
        for config_type in ["config", "secret"]:
            if os.path.exists(f"{MNT_PATH}/etc/{config_type}"):
                for file_or_dir in os.listdir(f"{MNT_PATH}/etc/{config_type}"):
                    if os.path.isfile(
                        os.path.join(f"{MNT_PATH}/etc/{config_type}", file_or_dir)
                    ):
                        if file_or_dir in ["replication.config"]:
                            LOG.info(
                                "Skipping symlink of {}, will be set up by PullReplicationConfigurator".format(
                                    file_or_dir
                                )
                            )
                        else:
                            self._symlink(
                                os.path.join(
                                    f"{MNT_PATH}/etc/{config_type}", file_or_dir
                                ),
                                os.path.join(etc_dir, file_or_dir),
                            )
