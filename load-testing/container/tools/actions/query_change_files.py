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

import json
import random
import traceback

import requests

from . import abstract

class QueryChangeFilesAction(abstract.AbstractAction):

  def __init__(self, change_id, url, user, pwd, probability=0.2):
    super().__init__(url, user, pwd, probability)
    self.change_id = change_id
    self.revision_id = 1

  def execute(self):
    if self._is_executed():
      try:
        response = requests.get(
          self._assemble_url(),
          auth=(self.user, self.pwd))
        files = list(json.loads(response.text.split("\n",1)[1]).keys())
        self._log_result(files)
        return files
      except Exception:
        self.failed = True
        self._log_result(traceback.format_exc().replace("\n", " "))

  def _assemble_url(self):
    return "%s/a/changes/%s/revisions/%s/files" % (
      self.url,
      self.change_id,
      self.revision_id)
