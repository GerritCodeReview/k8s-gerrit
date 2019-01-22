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

import os
import os.path
import re
import time

import git
import pytest
import requests

CONFIG_FILES = ["gerrit.config", "secure.config"]

class GerritSlaveTestSetup():

  def __init__(self, docker_client, docker_network, tmp_dir, image,
               gerrit_config, port):
    self.docker_client = docker_client
    self.docker_network = docker_network
    self.tmp_dir = tmp_dir
    self.image = image
    self.gerrit_config = gerrit_config
    self.port = port

    self.gerrit_container = None

    self._start_gerrit_container()

  def _create_config_files(self):
    tmp_config_dir = os.path.join(self.tmp_dir, "configs")
    if not os.path.isdir(tmp_config_dir):
      os.mkdir(tmp_config_dir)
    configs = {}
    for config in CONFIG_FILES:
      gerrit_config_file = os.path.join(tmp_config_dir, config)
      with open(gerrit_config_file, "w") as config_file:
        config_file.write(self.gerrit_config)
      configs[config] = gerrit_config_file
    return configs

  def _define_volume_mounts(self):
    volumes = {v: {
      "bind": "/var/config/%s" % k,
      "mode": "rw"
    } for (k, v) in self._create_config_files().items()}
    volumes[os.path.join(self.tmp_dir, "lib")] = {
      "bind": "/var/gerrit/lib",
      "mode": "rw"
    }
    return volumes

  def _start_gerrit_container(self):
    self.gerrit_container = self.docker_client.containers.run(
      image=self.image.id,
      user="gerrit",
      volumes=self._define_volume_mounts(),
      ports={
        str(self.port): str(self.port)
      },
      network=self.docker_network.name,
      detach=True,
      auto_remove=True
    )

  def stop_gerrit_container(self):
    self.gerrit_container.stop(timeout=1)


@pytest.fixture(scope="module")
def tmp_dir(tmp_path_factory):
  return tmp_path_factory.mktemp("gerrit-slave-test")

@pytest.fixture(scope="class")
def container_run_h2(request, docker_client, docker_network, tmp_dir,
                     gerrit_slave_image):
  config = """
    [gerrit]
      basePath = git

    [database]
      type = H2

    [httpd]
      listenUrl = http://*:8081

    [container]
      slave = true

    [test]
      success = True
    """
  test_setup = GerritSlaveTestSetup(
    docker_client, docker_network, tmp_dir, gerrit_slave_image, config, 8081)

  request.addfinalizer(test_setup.stop_gerrit_container)

  return test_setup.gerrit_container

@pytest.fixture(scope="class")
def container_run_mysql(request, docker_client, docker_network, tmp_dir,
                        gerrit_slave_image):
  config = """
    [gerrit]
      basePath = git

    [database]
      type = MySQL

    [httpd]
      listenUrl = http://*:8082

    [container]
      slave = true
    """
  test_setup = GerritSlaveTestSetup(
    docker_client, docker_network, tmp_dir, gerrit_slave_image, config, 8082)

  request.addfinalizer(test_setup.stop_gerrit_container)

  return test_setup.gerrit_container

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritSlaveH2(object):

  @pytest.fixture(params=CONFIG_FILES)
  def config_file_to_test(self, request):
    return request.param

  @pytest.fixture(params=["All-Users.git", "All-Projects.git"])
  def expected_repository(self, request):
    return request.param

  def test_gerrit_slave_gerrit_starts_up(self, container_run_h2):
    timeout = time.time() + 60
    while time.time() < timeout:
      last_log_line = container_run_h2.logs().decode("utf-8")
      if re.search(r"Gerrit Code Review .+ ready", last_log_line):
        break
      time.sleep(2)
    assert timeout > time.time()

  def test_gerrit_slave_custom_gerrit_config_available(
      self, container_run_h2, config_file_to_test):
    exit_code, output = container_run_h2.exec_run(
      "git config --file=/var/gerrit/etc/%s --get test.success" % config_file_to_test)
    output = output.decode("utf-8").strip()
    assert exit_code == 0
    assert output == "True"

  def test_gerrit_slave_repository_exists(self, container_run_h2, expected_repository):
    exit_code, _ = container_run_h2.exec_run(
      "test -d /var/gerrit/git/%s" % expected_repository)
    assert exit_code == 0

  def test_gerrit_slave_clone_repo_works(self, container_run_h2, tmp_path_factory):
    container_run_h2.exec_run("git init --bare /var/gerrit/git/test.git")
    clone_dest = tmp_path_factory.mktemp("gerrit_slave_clone_test")
    repo = git.Repo.clone_from("http://localhost:8081/test.git", clone_dest)
    assert repo.git_dir == os.path.join(clone_dest, ".git")

  def test_gerrit_slave_webui_not_accessible(self, container_run_h2):
    response = requests.get("http://localhost:8081")
    assert response.status_code == 404
    assert response.text == "Not Found"


@pytest.mark.slow
def test_gerrit_slave_downloads_mysql_driver(container_run_mysql):
  timeout = time.time() + 20
  while time.time() < timeout:
    _, output = container_run_mysql.exec_run(
      "find /var/gerrit/lib -name 'mysql-connector-java-*.jar'")
    output = output.decode("utf-8").strip()
    if re.match(r"/var/gerrit/lib/mysql-connector-java-.*\.jar", output):
      break

  assert timeout > time.time()
