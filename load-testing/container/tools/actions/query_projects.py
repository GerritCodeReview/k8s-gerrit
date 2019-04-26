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

DISALLOWED_PROJECTS = ["All-Projects", "All-Users"]

class QueryProjectsAction(abstract.AbstractAction):

  def __init__(self, url, user, pwd, probability=0.05):
    super().__init__(url, user, pwd, probability)

  def execute(self):
    if self._is_executed():
      selected_project = None
      try:
        rest_url = self._assemble_url()
        response = requests.get(
          rest_url,
          auth=(self.user, self.pwd))
        self.was_executed = True
        projects = list(json.loads(response.text.split("\n",1)[1]).keys())
        for project in DISALLOWED_PROJECTS:
          projects.remove(project)
        selected_project = random.choice(projects)
        self._log_result(selected_project)
      except Exception:
        self.failed = True
        self._log_result(traceback.format_exc().replace("\n", " "))

      return selected_project

  def _assemble_url(self):
    return "%s/a/projects/?d" % (self.url)
