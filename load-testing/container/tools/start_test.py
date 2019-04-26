#!/usr/bin/python3

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

import argparse
import logging
import random
import time

import numpy as np

from actions import *

class LoadTestInstance:

  def __init__(self, url, user, pwd, test_duration=None):
    self.url = url
    self.user = user
    self.pwd = pwd

    self.timeout = time.time() + test_duration if test_duration else None

    self.owned_projects = set()
    self.cloned_projects = set()

    self._create_initial_projects(2)

  def run(self):
    while True:
      if self.timeout and time.time() >= self.timeout:
        break

      self._wait_random_seconds(1, 10)

      self._exec_create_project_action()
      self._exec_list_projects_action()

      if self.owned_projects:
        self._exec_clone_project_action()

      if self.cloned_projects:
        self._exec_fetch_project_action()
        self._exec_push_project_action()

  def _create_initial_projects(self, num_init_projects):
    for _ in range(num_init_projects):
      self.owned_projects.add(CreateProjectAction(
        self.url,
        self.user,
        self.pwd,
        1.0).execute())

  def _wait_random_seconds(self, min, max):
    wait_duration = random.randint(min, max)
    time.sleep(wait_duration)

  def _choose_from_list_poisson(self, l):
    return np.random.choice(l, 1, p=np.random.poisson(20, len(l)))

  def _exec_create_project_action(self):
    action = CreateProjectAction(self.url, self.user, self.pwd)
    project_name = action.execute()
    if project_name:
      self.owned_projects.add(project_name)

    return action.was_executed

  def _exec_list_projects_action(self):
    action = ListProjectsAction(self.url, self.user, self.pwd)
    project_name = action.execute()
    if project_name:
      self.owned_projects.add(project_name)

    return action.was_executed

  def _exec_clone_project_action(self):
    action = CloneProjectAction(
      self.url,
      self.user,
      self.pwd,
      self._choose_from_list_poisson(list(self.owned_projects)))
    project_name = action.execute()
    if project_name:
      self.cloned_projects.add(project_name)
    return action.was_executed

  def _exec_fetch_project_action(self):
    action = FetchProjectAction(
      self._choose_from_list_poisson(list(self.cloned_projects)))
    action.execute()
    return action.was_executed

  def _exec_push_project_action(self):
    action = PushProjectAction(
      self._choose_from_list_poisson(list(self.cloned_projects)))
    action.execute()
    return action.was_executed



if __name__ == "__main__":

  logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(message)s'
  )

  parser = argparse.ArgumentParser()
  parser.add_argument(
    "-U",
    "--url",
    help="Gerrit base url",
    dest="url",
    action="store",
    required=True)

  parser.add_argument(
    "-u",
    "--user",
    help="Gerrit user",
    dest="user",
    action="store",
    default="admin")

  parser.add_argument(
    "-p",
    "--password",
    help="Gerrit password",
    dest="pwd",
    action="store",
    default="secret")

  parser.add_argument(
    "-d",
    "--duration",
    help="Test duration in seconds",
    dest="duration",
    action="store",
    type=int,
    default=None)

  args = parser.parse_args()

  test = LoadTestInstance(args.url, args.user, args.pwd, args.duration)
  test.run()
