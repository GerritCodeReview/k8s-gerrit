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

import abc
import logging
import random

import numpy as np

class AbstractAction(abc.ABC):

  def __init__(self, url, user, pwd, probability=1.0):
    self.url = url
    self.user = user
    self.pwd = pwd
    self.probability = probability
    self.was_executed = False
    self.failed = False

    self.log = logging.getLogger("ActionLogger")

  @abc.abstractmethod
  def execute(self):
    pass

  def _log_result(self, message=""):
    self.log.info("{action} {result} {message}".format(
      action = self.__class__.__name__,
      result = "FAILED" if self.failed else "OK",
      message = message
    ))

  def _is_executed(self):
    return np.random.choice(
      (True, False), 1, p=(self.probability, 1 - self.probability))
