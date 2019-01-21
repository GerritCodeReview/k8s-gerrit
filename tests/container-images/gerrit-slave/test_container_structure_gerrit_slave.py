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
import re

@pytest.fixture(scope="module")
def container_run(request, docker_client, gerrit_slave_image):
  container_run = docker_client.containers.run(
    image = gerrit_slave_image.id,
    entrypoint = "/bin/bash",
    command = ["-c", "tail -f /dev/null"],
    user = "gerrit",
    detach = True,
    auto_remove = True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run

@pytest.fixture(scope="function",
                params=["/var/tools/start", "/var/tools/download_db_driver"])
def expected_script(request):
  return request.param

def test_gerrit_slave_inherits_from_gerrit_base(gerrit_slave_image):
  containsTag = False
  for layer in gerrit_slave_image.history():
    containsTag = layer['Tags'] is not None and "gerrit-base:latest" in layer['Tags']
    if containsTag:
      break
  assert containsTag

def test_gerrit_slave_contains_expected_scripts(container_run, expected_script):
  exit_code, _ = container_run.exec_run("test -f %s" % expected_script)
  assert exit_code == 0

def test_gerrit_slave_contains_initialized_gerrit_site(container_run):
  exit_code, _ = container_run.exec_run("/var/gerrit/bin/gerrit.sh check")
  assert exit_code == 3

def test_gerrit_slave_contains_downloaded_mysql_driver(container_run):
  exit_code, output = container_run.exec_run(
    "find /var/gerrit/lib -name 'mysql-connector-java-*.jar'")
  output = output.decode("utf-8").strip()
  assert exit_code == 0
  assert re.match(r"/var/gerrit/lib/mysql-connector-java-.*\.jar", output)

def test_gerrit_slave_gerrit_is_configured_slave(container_run):
  exit_code, output = container_run.exec_run(
    "git config -f /var/gerrit/etc/gerrit.config --get container.slave")
  output = output.decode("utf-8").strip().lower()
  assert exit_code == 0
  assert output == "true"
