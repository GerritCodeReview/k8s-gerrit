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

import requests

from . import abstract
from .query_changes import QueryChangesAction

class ReviewChangeAction(abstract.AbstractAction):

  def __init__(self, url, user, pwd, probability=0.3):
    super().__init__(url, user, pwd, probability)
    self.change_id = QueryChangesAction(
      self.url, self.user, self.pwd, 1.0).execute()["change_id"]
    self.revision_id = 1

  def execute(self):
    if self._is_executed():
      try:
        rest_url = self._assemble_review_url()
        response = requests.post(
          rest_url,
          auth=(self.user, self.pwd),
          json=self._assemble_body())
        self.was_executed = True
      except:
        self.failed = True

      self._log_result()

  def _assemble_review_url(self):
    return "%s/a/changes/%s/revisions/%s/review" % (
      self.url,
      self.change_id,
      self.revision_id)

  def _assemble_list_files_url(self):
    return "%s/a/changes/%s/revisions/%s/files" % (
      self.url,
      self.change_id,
      self.revision_id)

  def _assemble_body(self):
    file_to_comment = random.choice(self._list_files())
    label = random.randint(-2, 2)
    return {
      "tag": "loadtest",
      "message": "Yet another comment.",
      "labels": {
        "Code-Review": label
      },
      "comments": {
        file_to_comment: [
          {
            "line": 1,
            "message": "Gibberish!"
          }
        ]
      }
    }

  def _list_files(self):
    response = requests.get(
      self._assemble_list_files_url(),
      auth=(self.user, self.pwd))

    return list(json.loads(response.text.split("\n",1)[1]).keys())
