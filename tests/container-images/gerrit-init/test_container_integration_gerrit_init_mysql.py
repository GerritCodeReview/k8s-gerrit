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

import utils

REVIEWDB_NAME = "reviewdb_test"

@pytest.fixture(scope="class")
def config_file_default(tmp_path_factory):
  tmp_config_dir = tmp_path_factory.mktemp('gerrit_init_config')
  with open(os.path.join(tmp_config_dir, "gerrit.config"), "w") as f:
    f.write("""
    [gerrit]
      basePath = git

    [database]
      type = mysql
      hostname = reviewdb
      port = 3306
      database = {db}
      url = jdbc:mysql://reviewdb:3306/{db}?nullNamePatternMatchesAll=true&useSSL=false

    [httpd]
      listenUrl = http://*:8080
    """.format(db=REVIEWDB_NAME))

  with open(os.path.join(tmp_config_dir, "secure.config"), "w") as f:
    f.write("""
    [database]
      username = root
      password = secret
    """)
  return tmp_config_dir

@pytest.fixture(scope="class", params=["slave = false", "slave = true"])
def config_file(request, config_file_default):
  with open(os.path.join(config_file_default, "gerrit.config"), "a") as f:
    f.write("""
    [container]
      %s
    """ % request.param)
  return config_file_default

@pytest.fixture(scope="class")
def mysql_container(request, docker_client, docker_network, mysql_container_factory):
  mysql_container = mysql_container_factory(docker_client, docker_network,
    "reviewdb", 3306, "secret")
  request.addfinalizer(mysql_container.stop_mysql_container)
  return mysql_container

@pytest.fixture(scope="class")
def container_run_factory(request, docker_client, docker_network, gerrit_init_image,
    tmp_path_factory):

  def container_run(config_path, entrypoint=None, command=None):
    empty_site = tmp_path_factory.mktemp("gerrit-init-site")
    container_run = docker_client.containers.run(
      image = gerrit_init_image.id,
      user = "gerrit",
      entrypoint = entrypoint,
      command = command,
      volumes = {
        empty_site: {
          "bind": "/var/gerrit/",
          "mode": "rw"
        },
        config_path: {
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

    return container_run, empty_site

  return container_run

@pytest.fixture(scope="class")
def container_run_mysql(container_run_factory, config_file):
  return container_run_factory(config_file)


@pytest.fixture(scope="class")
def container_run_mysql_validate_db(container_run_factory, config_file_default):
  container_run, _ = container_run_factory(
    config_file_default,
    entrypoint = "/var/tools/validate_db.py",
    command = ["-s", "/var/gerrit"])
  return container_run

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritInitWithMySQLDB(object):
  def test_gerrit_init_stops_to_wait_for_db(self, container_run_mysql):
    container_run, _ = container_run_mysql
    for _ in range(10):
      exit_code, _ = container_run.exec_run("test -f /var/gerrit/bin/gerrit.sh")
      assert exit_code == 1

  def test_gerrit_init_starts_if_db_server_is_available(self,
      container_run_mysql, mysql_container):
    _, site_path = container_run_mysql

    def wait_for_site_init():
      if os.path.exists(os.path.join(site_path, "bin", "gerrit.sh")):
        return None, True
      else:
        return None, False

    finished_in_time, _ = utils.exec_fn_with_timeout(wait_for_site_init, 20)
    assert finished_in_time

@pytest.mark.incremental
class TestGerritInitMySQLValidateDB(object):
  def test_db_server_is_found(self,
      container_run_mysql_validate_db, mysql_container):

    def wait_for_db_available():
      log = container_run_mysql_validate_db.logs().decode("utf-8")
      return log, re.search(r"Waiting for database to be available", log)

    finished_in_time, _ = utils.exec_fn_with_timeout(wait_for_db_available, 20)
    assert finished_in_time

  def test_gerrit_init_waits_for_configured_db(self,
      container_run_mysql_validate_db, mysql_container):

    for _ in range(10):
      log = container_run_mysql_validate_db.logs(tail=1).decode("utf-8")
      assert re.search(r"Still waiting", log)

  def test_gerrit_init_detects_database_available(self,
      container_run_mysql_validate_db, mysql_container):
    connection = mysql_container.connect()
    connection.execute("CREATE DATABASE %s;" % REVIEWDB_NAME)
    connection.close()

    def wait_for_database_available():
      log = container_run_mysql_validate_db.logs().decode("utf-8")
      return None, re.search(r"Found it", log)

    finished_in_time, _ = utils.exec_fn_with_timeout(
      wait_for_database_available, 20)
    assert finished_in_time

  def test_gerrit_init_waits_for_schema(self,
      container_run_mysql_validate_db, mysql_container):

    for _ in range(10):
      log = container_run_mysql_validate_db.logs(tail=1).decode("utf-8")
      assert re.search(r"Still waiting", log)

  def test_gerrit_init_detects_schema_available(self,
      container_run_mysql_validate_db, mysql_container):
    connection = mysql_container.connect()
    connection.execute("USE %s;" % REVIEWDB_NAME)
    connection.execute("CREATE TABLE changes (id int);")
    connection.execute("CREATE TABLE patch_sets (id int);")
    connection.close()

    def wait_for_database_schema_available():
      log = container_run_mysql_validate_db.logs().decode("utf-8")
      return None, re.search(r"Schema appears to have been created", log)

    finished_in_time, _ = utils.exec_fn_with_timeout(
      wait_for_database_schema_available, 20)
    assert finished_in_time
