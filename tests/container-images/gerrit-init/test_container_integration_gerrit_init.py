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

from docker.errors import NotFound

import os.path
import pytest
import re
import time

@pytest.fixture(scope="class", params=["slave = false", "slave = true"])
def config_file(tmp_path_factory, request):
  tmp_config_dir = tmp_path_factory.mktemp('gerrit_init_config')
  with open(os.path.join(tmp_config_dir, "gerrit.config"), "w") as f:
    f.write("""
    [gerrit]
      basePath = git

    [database]
      type = mysql
      hostname = reviewdb
      port = 3306
      database = reviewdb
      url = jdbc:mysql://reviewdb:3306/reviewdb?nullNamePatternMatchesAll=true&useSSL=false

    [httpd]
      listenUrl = http://*:8080

    [container]
      %s
    """ % request.param)

  with open(os.path.join(tmp_config_dir, "secure.config"), "w") as f:
    f.write("""
    [database]
      username = root
      password = secret
    """)
  return tmp_config_dir

@pytest.fixture(scope="class")
def slave_config_file(config_file):
  with open(os.path.join(config_file, "gerrit.config"), "w") as f:
    f.append("""
    [container]
      slave = true
    """)

@pytest.fixture(scope="class")
def mysql_container(request, docker_client, docker_network, mysql_container_factory):
  mysql_container = mysql_container_factory(docker_client, docker_network,
    "reviewdb", 3306, "secret")
  request.addfinalizer(mysql_container.stop_mysql_container)
  return mysql_container

@pytest.fixture(scope="class")
def container_run_default(request, docker_client, gerrit_init_image):
  container_run = docker_client.containers.run(
    image = gerrit_init_image.id,
    user = "gerrit",
    detach = True,
    auto_remove = True
  )
  return container_run

@pytest.fixture(scope="class")
def container_run_mysql(request, docker_client, docker_network, gerrit_init_image,
    tmp_path_factory, config_file):
  empty_site = tmp_path_factory.mktemp("gerrit-init-site")
  container_run = docker_client.containers.run(
    image = gerrit_init_image.id,
    user = "gerrit",
    volumes = {
      empty_site: {
        "bind": "/var/gerrit/",
        "mode": "rw"
      },
      config_file: {
        "bind": "/var/gerrit/etc",
        "mode": "rw"
      }
    },
    network = docker_network.name,
    detach = True,
    auto_remove = True
  )

  def stop_container():
    try:
      container_run.stop(timeout=1)
    except:
      print("Container already stopped.")

  request.addfinalizer(stop_container)

  return container_run

@pytest.fixture(scope="class")
def container_run_endless(request, docker_client, gerrit_init_image):
  container_run = docker_client.containers.run(
    image = gerrit_init_image.id,
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

@pytest.mark.incremental
class TestGerritInitEmptySite(object):
  def test_gerrit_init_gerrit_is_initialized(self, container_run_default):
    timeout = time.time() + 60
    while time.time() < timeout:
      log = container_run_default.logs().decode("utf-8")
      if re.search(r"Initialized /var/gerrit", log):
        break
    assert timeout > time.time()

  def test_gerrit_init_exits_after_init(self, container_run_default):
    timeout = time.time() + 60
    while time.time() < timeout:
      try:
        container_run_default.reload()
      except NotFound:
        break
    assert timeout > time.time()
    assert container_run_default.attrs["State"]["ExitCode"] == 0

@pytest.mark.incremental
class TestGerritInitWithPlugins(object):
  def test_gerrit_init_plugins_are_installed(self, container_run_endless):
    exit_code, _ = container_run_endless.exec_run(
      "/var/tools/gerrit_init.py -s /var/gerrit -p replication -p reviewnotes")
    assert exit_code == 0
    cmd = "/bin/bash -c '" + \
      "test -f /var/gerrit/plugins/replication.jar && " + \
      "test -f /var/gerrit/plugins/reviewnotes.jar'"
    exit_code, output = container_run_endless.exec_run(cmd)
    print(output)
    assert exit_code == 0

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritInitWithMySQLDB(object):
  def test_gerrit_init_stops_to_wait_for_db(self, container_run_mysql):
    for _ in range(10):
      exit_code, _ = container_run_mysql.exec_run("test -f /var/gerrit/bin/gerrit.sh")
      print(container_run_mysql.logs())
      assert exit_code == 1

  def test_gerrit_init_starts_if_db_server_is_available(self,
      container_run_mysql, mysql_container):
    timeout = time.time() + 60
    while time.time() < timeout:
      log = container_run_mysql.logs().decode("utf-8")
      if re.search(r"Initialized /var/gerrit", log):
        break
    assert timeout > time.time()
