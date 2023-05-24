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

import os
import subprocess
import time

from ..helpers import log

LOG = log.get_logger("init")


class NoteDbValidator:
    def __init__(self, site):
        self.site = site

        self.notedb_repos = ["All-Projects.git", "All-Users.git"]
        self.required_refs = {
            "All-Projects.git": ["refs/meta/config", "refs/meta/version"],
            "All-Users.git": ["refs/meta/config"],
        }

    def _test_repo_exists(self, repo):
        return os.path.exists(os.path.join(self.site, "git", repo))

    def _test_ref_exists(self, repo, ref):
        command = f"git --git-dir {self.site}/git/{repo} rev-parse --verify {ref}"
        git_show_ref = subprocess.run(
            command.split(),
            stdout=subprocess.PIPE,
            universal_newlines=True,
            check=False,
        )

        return git_show_ref.returncode == 0

    def wait_until_valid(self):
        for repo in self.notedb_repos:
            LOG.info("Waiting for repository %s.", repo)
            while not self._test_repo_exists(repo):
                time.sleep(1)
            LOG.info("Found %s.", repo)

            for ref in self.required_refs[repo]:
                LOG.info("Waiting for ref %s in repository %s.", ref, repo)
                while not self._test_ref_exists(repo, ref):
                    time.sleep(1)
                LOG.info("Found ref %s in repo %s.", ref, repo)

    def check(self):
        for repo in self.notedb_repos:
            if not self._test_repo_exists(repo):
                LOG.info("Repository %s is missing.", repo)
                return False
            LOG.info("Found %s.", repo)

            for ref in self.required_refs[repo]:
                if not self._test_ref_exists(repo, ref):
                    LOG.info("Ref %s in repository %s is missing.", ref, repo)
                    return False
                LOG.info("Found ref %s in repo %s.", ref, repo)
        return True
