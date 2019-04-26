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

import os.path
import shutil
import traceback

import git

from . import abstract

class FetchProjectAction(abstract.AbstractAction):

  def __init__(self, project_name, probability=0.1):
    super().__init__(url=None, user=None, pwd=None, probability=probability)
    self.project_name = project_name

  def execute(self):
    local_repo_path = os.path.join("/tmp", self.project_name)
    if os.path.exists(local_repo_path) and self._is_executed():
      try:
        repo = git.Repo(local_repo_path)
        for remote in repo.remotes:
            remote.fetch()
        self.was_executed = True
        self._log_result(self.project_name)
      except Exception:
        self.failed = True
        self._log_result(traceback.format_exc().replace("\n", " "))
