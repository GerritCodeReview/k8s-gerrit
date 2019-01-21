# pylint: disable=W0613

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

import os.path
import re
import time

import pytest
import requests

CONFIG_FILES = ["gerrit.config", "secure.config", "replication.config"]

@pytest.fixture(scope="module")
def config_files(tmp_path_factory):
  tmp_config_dir = tmp_path_factory.mktemp('gerrit_master_config')
  configs = {}
  for config in CONFIG_FILES:
    gerrit_config_file = os.path.join(tmp_config_dir, config)
    with open(gerrit_config_file, "w") as config_file:
      config_file.write("""
      [gerrit]
        basePath = git

      [database]
        type = H2

      [httpd]
        listenUrl = http://*:8081

      [test]
        success = True
      """)
    configs[config] = gerrit_config_file

  return configs

@pytest.fixture(scope="module")
def container_run(request, docker_client, gerrit_master_image, config_files):
  container_run = docker_client.containers.run(
    image=gerrit_master_image.id,
    user="gerrit",
    volumes={v: {
      "bind": "/var/config/%s" % k,
      "mode": "rw"
    } for (k, v) in config_files.items()},
    ports={
      "8081": "8081"
    },
    detach=True,
    auto_remove=True
  )

  def stop_container():
    container_run.stop(timeout=1)

  request.addfinalizer(stop_container)

  return container_run

@pytest.fixture(params=CONFIG_FILES)
def config_file_to_test(request):
  return request.param

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritMasterStartScript(object):
  def test_gerrit_master_gerrit_starts_up(self, container_run):
    timeout = time.time() + 60
    while time.time() < timeout:
      last_log_line = container_run.logs().decode("utf-8")
      if re.search(r"Gerrit Code Review .+ ready", last_log_line):
        break
      time.sleep(2)
    assert timeout > time.time()

  def test_gerrit_master_custom_gerrit_config_available(
      self, container_run, config_file_to_test):
    exit_code, output = container_run.exec_run(
      "git config --file=/var/gerrit/etc/%s --get test.success" % config_file_to_test)
    output = output.decode("utf-8").strip()
    assert exit_code == 0
    assert output == "True"

  def test_gerrit_master_httpd_is_responding(self, container_run):
    response = requests.get("http://localhost:8081")
    assert response.status_code == 200
    assert re.search(r'content="Gerrit Code Review"', response.text)
