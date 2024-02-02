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
from .init import GerritInit

LOG = log.get_logger("init")
MNT_PATH = "/var/mnt"


class GerritInitHA(GerritInit):
    def __init__(self, site, config):
        super().__init__(site, config)

    def _symlink_git_and_shared_volume(self):
        self._symlink(f"{MNT_PATH}/git", f"{self.site}/git")
        mounted_shared_dir = f"{MNT_PATH}/shared"
        if not self.is_replica and os.path.exists(mounted_shared_dir):
            self._symlink(mounted_shared_dir, f"{self.site}/shared")

    def _symlink_mounted_site_components(self):
        self._symlink_git_and_shared_volume()
        self._symlink_index()
        self._symlink_or_make_data_dir()
