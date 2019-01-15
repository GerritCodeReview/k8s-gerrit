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

import pytest

@pytest.fixture(scope="module")
def container_run(request, docker_client, mysql_replication_init_image):
  container_run = docker_client.containers.run(
    image = mysql_replication_init_image.id,
    entrypoint = "/bin/bash",
    command = ["-c", "tail -f /dev/null"],
    detach = True,
    auto_remove = True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run


def test_mysql_replication_init_contains_mysql_client(container_run):
  exit_code, _ = container_run.exec_run(
    "which mysql"
  )
  assert exit_code == 0

def test_mysql_replication_init_contains_start_script(container_run):
  exit_code, _ = container_run.exec_run(
    "test -f /var/tools/start"
  )
  assert exit_code == 0

def test_mysql_replication_init_has_entrypoint(mysql_replication_init_image):
  entrypoint = mysql_replication_init_image.attrs["ContainerConfig"]["Entrypoint"]
  assert len(entrypoint) >= 1
  assert "/var/tools/start" in entrypoint
