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
import subprocess
import sys

from ..helpers import log
from .init import GerritInit
from .reindex import get_reindexer
from .validate_notedb import NoteDbValidator

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

    def execute(self):
        # Required for migration away from NFS-based log storage, when using the
        # Gerrit-Operator and to provide backwards compatibility for the helm-
        # charts
        self._symlink(f"{MNT_PATH}/logs", f"{self.site}/logs", required=False)

        if not self.is_replica:
            self._symlink_mounted_site_components()
        elif not NoteDbValidator(MNT_PATH).check():
            LOG.info("NoteDB not ready. Initializing repositories.")
            self._symlink_mounted_site_components()
        self._symlink_configuration()

        if os.path.exists(self.pid_file):
            os.remove(self.pid_file)

        self.plugin_installer.execute()

        if self._needs_init():
            if self.gerrit_config:
                LOG.info("Existing gerrit.config found.")
                dev_option = (
                    "--dev"
                    if self.gerrit_config.get(
                        "auth.type", "development_become_any_account"
                    ).lower()
                    == "development_become_any_account"
                    else ""
                )
            else:
                LOG.info("No gerrit.config found. Initializing default site.")
                dev_option = "--dev"

            flags = f"--no-auto-start --batch {dev_option}"

            command = f"java -jar /var/war/gerrit.war init {flags} -d {self.site}"

            init_process = subprocess.run(
                command.split(), stdout=subprocess.PIPE, check=True
            )

            if init_process.returncode > 0:
                LOG.error(
                    "An error occurred, when initializing Gerrit. Exit code: %d",
                    init_process.returncode,
                )
                sys.exit(1)

            self._remove_auto_generated_ssh_keys()
            self._symlink_configuration()

            if self.is_replica:
                self._symlink_mounted_site_components()

        get_reindexer(self.site, self.config).start(self.force_offline_reindex)
