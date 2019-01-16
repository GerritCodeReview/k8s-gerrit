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

from sqlalchemy import create_engine

import os.path
import pytest
import re
import time

MYSQL_HOST = "k8sgerrit-mysql"
MYSQL_PORT = 3306
MYSQL_ROOT_PASSWORD = "big_secret"
REPL_PASSWORD = "test"

@pytest.fixture()
def mock_dump():
  return ("CREATE DATABASE `users`;"
          "USE `users`;"
          "CREATE TABLE `users` ("
          "`id` INT(11) NOT NULL,"
          "`first_name` VARCHAR(50),"
          "`last_name` VARCHAR(50),"
          "`password` VARCHAR(100),"
          "PRIMARY KEY (`id`))")

@pytest.fixture(scope="module")
def mock_sql_script(tmp_path_factory):
  tmp_dir = tmp_path_factory.mktemp("mysql_init_script")
  with open(os.path.join(tmp_dir, "initialize-slave.sql"), "w") as f:
    f.write(("USE `users`;"
            "SET @query = CONCAT("
            "\"INSERT INTO `users` (id, first_name, last_name, password) \","
            "\"VALUES (1, 'John', 'Doe', '\", @replpwd, \"');\");"
            "PREPARE stmt FROM @query;"
            "EXECUTE stmt;"))
  return tmp_dir

@pytest.fixture(scope="module")
def mysql_container(request, docker_client, docker_network, mysql_container_factory):
  mysql_container = mysql_container_factory(docker_client, docker_network,
    MYSQL_HOST, MYSQL_PORT, MYSQL_ROOT_PASSWORD)

  request.addfinalizer(mysql_container.stop_mysql_container)

  return mysql_container

@pytest.fixture(scope="module")
def init_container(request, docker_client, docker_network,
    mysql_replication_init_image, mock_sql_script):
  container_run = docker_client.containers.run(
    image = mysql_replication_init_image.id,
    environment = {
      "MYSQL_HOST": MYSQL_HOST,
      "MYSQL_PORT": MYSQL_PORT,
      "MYSQL_ROOT_PASSWORD": MYSQL_ROOT_PASSWORD,
      "REPL_PASSWORD": REPL_PASSWORD
    },
    volumes = {
      mock_sql_script: {
        "bind": "/var/sql",
        "mode": "ro"
      }
    },
    network = docker_network.name,
    name = "mysql-replication-init",
    detach = True
  )

  def stop_container():
    container_run.stop(timeout=1)
    container_run.remove(v=True, force=True)

  request.addfinalizer(stop_container)

  return container_run

@pytest.fixture(scope="module")
def containers(mysql_container, init_container):
  return mysql_container, init_container

@pytest.mark.slow
@pytest.mark.incremental
class TestMysqlInitScript(object):
  def test_mysql_replication_init_waiting_for_dump(self, containers):
    (_, init_container) = containers
    timeout = time.time() + 20
    while time.time() < timeout:
      last_log_line = init_container.logs(tail=1).decode("utf-8").strip()
      if last_log_line == \
          "Waiting for database dump file at /var/data/db/master_dump.sql":
        break
    assert timeout > time.time()

  def test_mysql_replication_init_accepts_dump(self, containers, mock_dump):
    (_, init_container) = containers
    cmd = "/bin/bash -c \"echo '%s' > /var/data/db/master_dump.sql\"" % mock_dump
    init_container.exec_run(cmd)
    timeout = time.time() + 20
    while time.time() < timeout:
      logs = init_container.logs().decode("utf-8")
      if re.search(r"Database dump received", logs):
        break
    assert timeout > time.time()

  def test_mysql_replication_init_finishes(self, containers):
    (_, init_container) = containers
    timeout = time.time() + 20
    while time.time() < timeout:
      init_container.reload()
      if init_container.status == "exited":
        break
    assert timeout > time.time()
    assert init_container.attrs["State"]["ExitCode"] == 0

  def test_mysql_replication_init_applies_dump(self, containers):
    (mysql_container, _) = containers
    connection = mysql_container.connect()
    result = connection.execute("SHOW DATABASES;")
    connection.close()
    assert "users" in [row[0] for row in result]

  def test_mysql_replication_init_runs_slave_init_script(self, containers):
    (mysql_container, _) = containers
    connection = mysql_container.connect()
    result = connection.execute("USE `users`;")
    result = connection.execute("SELECT password FROM users WHERE id=1 LIMIT 1;")
    connection.close()
    for row in result:
      assert REPL_PASSWORD in row["password"]
