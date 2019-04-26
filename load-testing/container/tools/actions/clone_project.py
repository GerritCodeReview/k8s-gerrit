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

import git

from . import abstract

class CloneProjectAction(abstract.AbstractAction):

  def __init__(self, url, user, pwd, project_name, probability=0.01157):
    super().__init__(url, user, pwd, probability)
    self.project_name = project_name

  def execute(self):
    if self._is_executed():
      local_repo_path = os.path.join("/tmp", self.project_name)
      if os.path.exists(local_repo_path):
        shutil.rmtree(local_repo_path)
      self.log.info("Cloning project %s", self.project_name)
      repo = git.Repo.clone_from(self._assemble_url(), local_repo_path)
      self.was_executed = True
      return self.project_name

  def _assemble_url(self):
    url = "%s/%s.git" % (self.url, self.project_name)
    return url.replace("//", "//%s:%s@" % (self.user, self.pwd))
