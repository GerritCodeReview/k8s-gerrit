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

import enum
import os
import random
import shutil
import string
import traceback

import git

from . import abstract

class AbstractPushAction(abstract.AbstractAction):

  def __init__(self, refspec, project_name, probability=0.2):
    super().__init__(url=None, user=None, pwd=None, probability=probability)
    self.project_name = project_name
    self.CHANGE_TYPES = [
      self._add_file,
      self._modify_file,
      self._delete_file
    ]
    self.local_repo_path = os.path.join("/tmp", self.project_name)
    self.repo = git.Repo(self.local_repo_path)
    self.refspec = refspec

  def _push(self):
    self.repo.git.checkout('origin/master')
    if os.path.exists(self.local_repo_path) and self._is_executed():
      try:
        num_commits = random.randint(1, 5)
        for _ in range(num_commits):
          self._create_commit()
        self.repo.remotes.origin.push(refspec=self.refspec)
        self.was_executed = True
        self._log_result("Pushed %d commits to project %s using refspec %s" % (
          num_commits,
          self.project_name,
          self.refspec))
      except Exception:
        self.failed = True
        self._log_result(traceback.format_exc().replace("\n", " "))

  def _create_commit(self):
    change_type_choice = random.choice(self.CHANGE_TYPES)
    change_type_choice()
    self.repo.index.commit("%s\n\n" % self._create_random_string(16))

  def _add_file(self):
    while True:
      file_name = self._create_random_string(8)
      file_path = os.path.join("/tmp", self.project_name, file_name)
      if os.path.exists(file_path):
        continue
      with open(file_path, "w+") as f:
        self.log.info("Adding file %s to commit", file_name)
        f.write(self._create_random_string(random.randint(1, 2000)))
      self.repo.index.add([file_name])
      break

  def _modify_file(self):
    files = self._get_files_in_repo_workdir()
    if len(files) <= 0:
      self.log.info("Repository does not contain any files yet. Adding instead of modifying")
      self._add_file()
      return
    file_name = random.choice(files)
    file_path = os.path.join("/tmp", self.project_name, file_name)
    with open(file_path, "a+") as f:
      self.log.info("Modifying file %s for commit", file_name)
      f.write(self._create_random_string(random.randint(1, 2000)))
    self.repo.index.add([file_name])

  def _delete_file(self):
    files = self._get_files_in_repo_workdir()
    if len(files) <= 0:
      self.log.info("Repository does not contain any files yet. Adding instead of deleting")
      self._add_file()
      return
    file_name = random.choice(files)
    self.log.info("Deleting file %s to commit", file_name)
    os.remove(os.path.join("/tmp", self.project_name, file_name))
    self.repo.index.remove([file_name])

  def _get_files_in_repo_workdir(self):
    files = list()
    for (dirpath, dirnames, filenames) in os.walk(os.path.join("/tmp", self.project_name)):
      if ".git" not in dirpath:
        files += [os.path.join(dirpath, file) for file in filenames]
    return files

  def _create_random_string(self, length):
    allowed_symbols = string.ascii_letters + string.digits
    return "".join(
      [random.choice(allowed_symbols) for n in range(length)])
