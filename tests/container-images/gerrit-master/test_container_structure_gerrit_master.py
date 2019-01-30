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

import pytest

import utils

@pytest.fixture(scope="module")
def container_run(request, docker_client, gerrit_master_image):
  container_run = docker_client.containers.run(
    image=gerrit_master_image.id,
    user="gerrit",
    detach=True,
    auto_remove=True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run

# pylint: disable=E1101
def test_gerrit_master_inherits_from_gerrit_base(gerrit_master_image):
  assert utils.check_if_ancestor_image_is_inherited(
    gerrit_master_image, "gerrit-base:latest")

def test_gerrit_master_contains_start_script(container_run):
  exit_code, _ = container_run.exec_run("test -f /var/tools/start")
  assert exit_code == 0
