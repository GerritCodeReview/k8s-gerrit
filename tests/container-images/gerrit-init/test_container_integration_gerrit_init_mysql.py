# pylint: disable=E1101, W0613

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

import os.path
import re
import time

from docker.errors import NotFound
from requests.exceptions import ConnectionError as RequestsConnectionError

import pytest

MYSQL_CONFIG = {
  "MYSQL_HOST": "reviewdb",
  "MYSQL_PORT": 3306,
  "MYSQL_ROOT_PASSWORD": "secret",
  "REPL_PASSWORD": "test"
}

@pytest.fixture(scope="class")
def config_default(tmp_path_factory):
  gerrit_config = """
    [gerrit]
      basePath = git

    [database]
      type = mysql
      hostname = {host}
      port = {port}
      database = {host}
      url = jdbc:mysql://{host}:{port}/reviewdb?nullNamePatternMatchesAll=true&useSSL=false

    [httpd]
      listenUrl = http://*:8080
    """.format(host=MYSQL_CONFIG["MYSQL_HOST"], port=MYSQL_CONFIG["MYSQL_PORT"])

  secure_config = """
    [database]
      username = root
      password = {password}
    """.format(password=MYSQL_CONFIG["MYSQL_ROOT_PASSWORD"])

  return gerrit_config, secure_config

@pytest.fixture(scope="class")
def config_file_default(tmp_path_factory, config_default):
  gerrit_config, secure_config = config_default
  tmp_config_dir = tmp_path_factory.mktemp('gerrit_init_config')
  with open(os.path.join(tmp_config_dir, "gerrit.config"), "w") as gerrit_config_file:
    gerrit_config_file.write(gerrit_config)
  with open(os.path.join(tmp_config_dir, "secure.config"), "w") as secure_config_file:
    secure_config_file.write(secure_config)
  return tmp_config_dir

@pytest.fixture(scope="class", params=["slave = false", "slave = true"])
def config_file(request, config_file_default):
  with open(os.path.join(config_file_default, "gerrit.config"), "a") as gerrit_config:
    gerrit_config.write("""
    [container]
      %s
    """ % request.param)
  return config_file_default

@pytest.fixture(scope="class")
def mysql_container(docker_client, docker_network, mysql_container_factory):
  mysql_container = mysql_container_factory(
    docker_client, docker_network, MYSQL_CONFIG)
  mysql_container.start()

  yield mysql_container.mysql_container

  mysql_container.stop()

@pytest.fixture(scope="module")
def tmp_dir(tmp_path_factory):
  return tmp_path_factory.mktemp("gerrit-slave-test")

@pytest.fixture(scope="class")
def gerrit_slave_container(docker_client, docker_network, tmp_dir,
                           gerrit_slave_image, gerrit_container_factory,
                           config_default):
  configs = {
    "gerrit.config": config_default[0],
    "secure.config": config_default[1],
  }
  test_setup = gerrit_container_factory(
    docker_client, docker_network, tmp_dir, gerrit_slave_image, configs, 8080)
  test_setup.start()

  yield test_setup.gerrit_container

  test_setup.stop()

@pytest.fixture(scope="class")
def container_run_factory(request, docker_client, docker_network, gerrit_init_image,
                          tmp_path_factory):

  def container_run(config_path, entrypoint=None, command=None):
    empty_site = tmp_path_factory.mktemp("gerrit-init-site")
    container_run = docker_client.containers.run(
      image=gerrit_init_image.id,
      user="gerrit",
      entrypoint=entrypoint,
      command=command,
      volumes={
        empty_site: {
          "bind": "/var/gerrit/",
          "mode": "rw"
        },
        config_path: {
          "bind": "/var/gerrit/etc",
          "mode": "rw"
        }
      },
      network=docker_network.name,
      detach=True,
      auto_remove=True
    )

    def stop_container():
      try:
        container_run.stop(timeout=1)
      except NotFound:
        print("Container already stopped.")

    request.addfinalizer(stop_container)

    return container_run, empty_site

  return container_run

@pytest.fixture(scope="class")
def container_run_mysql(container_run_factory, config_file):
  return container_run_factory(config_file)

@pytest.fixture(scope="class")
def container_run_gerrit_init_manual(container_run_factory, config_file_default):
  return container_run_factory(
    config_file_default,
    entrypoint="tail",
    command=["-f", "/dev/null"])

@pytest.fixture(scope="class")
def container_run_gerrit_init_validatedb(container_run_factory, config_file_default):
  return container_run_factory(
    config_file_default,
    entrypoint="/var/tools/validate_db.py",
    command=["-s", "/var/gerrit"])

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritInitWithMySQLDB:
  def test_gerrit_init_stops_to_wait_for_db(self, container_run_mysql):
    container_run, _ = container_run_mysql
    for _ in range(10):
      exit_code, _ = container_run.exec_run("test -f /var/gerrit/bin/gerrit.sh")
      assert exit_code == 1

  @pytest.mark.timeout(20)
  def test_gerrit_init_starts_if_db_server_is_available(
      self, container_run_mysql, mysql_container):
    _, site_path = container_run_mysql
    script_path = os.path.join(site_path, "bin", "gerrit.sh")

    while not os.path.exists(script_path):
      time.sleep(1)

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritInitMySQLForSlave:
  def test_site_initialized(self, container_run_gerrit_init_manual,
                            mysql_container):
    container_run, site_path = container_run_gerrit_init_manual
    container_run.exec_run(
      "git config -f /var/gerrit/etc/gerrit.config container.slave true"
    )
    container_run.exec_run(
      "/var/tools/gerrit_init.py -s /var/gerrit -c /var/config/default.config.yaml"
    )

    assert os.path.exists(os.path.join(site_path, "bin", "gerrit.sh"))

  @pytest.mark.timeout(20)
  def test_db_validation_finishes_successfully(
      self, container_run_gerrit_init_manual, mysql_container):
    container_run, _ = container_run_gerrit_init_manual

    exit_code, _ = container_run.exec_run(
      "/var/tools/validate_db.py -s /var/gerrit"
    )

    assert exit_code == 0

  @pytest.mark.timeout(60)
  def test_db_gerrit_slave_starts_up(self, mysql_container, gerrit_slave_container):
    def wait_for_gerrit_start():
      try:
        log = gerrit_slave_container.logs().decode("utf-8")
        return re.search(r"Gerrit Code Review .+ ready", log)
      except AttributeError:
        return None

    while not wait_for_gerrit_start():
      time.sleep(1)

@pytest.mark.slow
@pytest.mark.incremental
class TestGerritInitDBValidation:

  def test_db_validation_waits_without_db_server(
      self, container_run_gerrit_init_validatedb):
    container_run, _ = container_run_gerrit_init_validatedb

    # There should be a ReadTimeout exception thrown by requests, but Error
    # handling seems to have changed. This is a known issue:
    # https://github.com/docker/docker-py/issues/1966.
    # For now, a requests.exceptions.ConnectionError is expected, which is
    # raised, when running in a timeout.
    with pytest.raises(RequestsConnectionError):
      container_run.wait(timeout=20)

  def test_db_validation_waits_without_db(
      self, container_run_gerrit_init_validatedb, mysql_container):
    container_run, _ = container_run_gerrit_init_validatedb
    with pytest.raises(RequestsConnectionError):
      container_run.wait(timeout=20)

  def test_db_validation_waits_without_db_schema(
      self, container_run_gerrit_init_validatedb, mysql_container):
    container_run, _ = container_run_gerrit_init_validatedb
    mysql_container.exec_run(
      "mysql -u root -p{password} -e 'CREATE DATABASE reviewdb;'".format(
        password=MYSQL_CONFIG["MYSQL_ROOT_PASSWORD"])
    )
    with pytest.raises(RequestsConnectionError):
      container_run.wait(timeout=20)

  def test_db_validation_finishes_with_db_schema(
      self, container_run_gerrit_init_validatedb, mysql_container):
    container_run, _ = container_run_gerrit_init_validatedb
    mysql_cmd = """
      mysql -u root -p{password} -e '
      USE reviewdb;
      CREATE TABLE changes (id int);
      CREATE TABLE patch_sets (id int);'
    """.format(password=MYSQL_CONFIG["MYSQL_ROOT_PASSWORD"])
    mysql_container.exec_run(mysql_cmd)
    container_status = container_run.wait(timeout=20)
    assert container_status["StatusCode"] == 0
