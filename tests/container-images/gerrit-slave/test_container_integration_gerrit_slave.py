# pylint: disable=W0613, E1101

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

from glob import glob

import os
import os.path
import re

import git
import pytest
import requests

CONFIG_FILES = ["gerrit.config", "secure.config"]

@pytest.fixture(scope="module")
def tmp_dir(tmp_path_factory):
  return tmp_path_factory.mktemp("gerrit-slave-test")

@pytest.fixture(scope="class")
def container_run_h2(request, docker_client, docker_network, tmp_dir,
                     gerrit_slave_image, gerrit_container_factory):
  configs = {
    "gerrit.config": """
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
      """,
    "secure.config": """
      [test]
          success = True
      """}
  test_setup = gerrit_container_factory(
    docker_client, docker_network, tmp_dir, gerrit_slave_image, configs, 8081)
  test_setup.start()

  request.addfinalizer(test_setup.stop)

  return test_setup.gerrit_container

@pytest.fixture(scope="class")
def container_run_mysql(request, docker_client, docker_network, tmp_dir,
                        gerrit_slave_image, gerrit_container_factory):
  configs = {
    "gerrit.config": """
      [gerrit]
        basePath = git

      [database]
        type = MySQL

      [httpd]
        listenUrl = http://*:8082

      [container]
        slave = true
      """}
  test_setup = gerrit_container_factory(
    docker_client, docker_network, tmp_dir, gerrit_slave_image, configs, 8082)
  test_setup.start()

  request.addfinalizer(test_setup.stop)

  return test_setup.gerrit_container

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritSlaveH2:

  @pytest.fixture(params=CONFIG_FILES)
  def config_file_to_test(self, request):
    return request.param

  @pytest.fixture(params=["All-Users.git", "All-Projects.git"])
  def expected_repository(self, request):
    return request.param

  @pytest.mark.timeout(60)
  def test_gerrit_slave_gerrit_starts_up(self, container_run_h2):
    def wait_for_gerrit_start():
      log = container_run_h2.logs().decode("utf-8")
      return re.search(r"Gerrit Code Review .+ ready", log)

    while not wait_for_gerrit_start():
      continue

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
@pytest.mark.timeout(20)
def test_gerrit_slave_downloads_mysql_driver(container_run_mysql, tmp_dir):

  def wait_for_mysql_driver_download():
    _, output = container_run_mysql.exec_run(
      "find /var/gerrit/lib -name 'mysql-connector-java-*.jar'")
    output = output.decode("utf-8").strip()
    return re.match(r"/var/gerrit/lib/mysql-connector-java-.*\.jar", output)

  while not wait_for_mysql_driver_download():
    continue

  driver_path_pattern = os.path.join(tmp_dir, "lib", "mysql-connector-java-*.jar")
  lib_files = [f for f in glob(driver_path_pattern) if os.path.isfile(f)]
  assert lib_files
