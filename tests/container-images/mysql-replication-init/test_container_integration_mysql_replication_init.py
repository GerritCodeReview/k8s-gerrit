from sqlalchemy import create_engine

import os.path
import pytest
import time

MYSQL_HOST = "k8sgerrit-mysql"
MYSQL_PORT = 3306
MYSQL_ROOT_PASSWORD = "big_secret"
REPL_PASSWORD = "test"

def connect_to_mysql():
  engine = create_engine("mysql+pymysql://root:%s@localhost:%s" % (
        MYSQL_ROOT_PASSWORD, MYSQL_PORT))
  return engine.connect()

def wait_for_db_connection():
  connection = None
  while connection is None:
    try:
      connection = connect_to_mysql()
      continue
    except:
      time.sleep(1)
  connection.close()

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
def mysql_container(request, docker_client, docker_network):
  mysql_container = docker_client.containers.run(
    image = "mysql:5.7",
    environment = {
      "MYSQL_ROOT_PASSWORD": MYSQL_ROOT_PASSWORD
    },
    ports = {
      "3306":"3306"
    },
    network = docker_network.name,
    name = MYSQL_HOST,
    detach = True,
    auto_remove = True
  )

  wait_for_db_connection()

  def stop_container():
    mysql_container.stop(timeout=1)

  request.addfinalizer(stop_container)

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

  time.sleep(3)

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
    last_log_line = init_container.logs(tail=1).decode("utf-8").strip()
    assert last_log_line == \
      "Waiting for database dump file at /var/data/db/master_dump.sql"

  def test_mysql_replication_init_accepts_dump(self, containers, mock_dump):
    (_, init_container) = containers
    cmd = "/bin/bash -c \"echo '%s' > /var/data/db/master_dump.sql\"" % mock_dump
    init_container.exec_run(cmd)
    timeout = time.time() + 20
    while time.time() < timeout:
      logs = [
        line.strip() for line in init_container.logs().decode("utf-8").splitlines()
      ]
      if "done" in logs:
        break
    assert timeout > time.time()

  def test_mysql_replication_init_runs_slave_init_script(self, containers):
    connection = connect_to_mysql()
    result = connection.execute("USE `users`;")
    result = connection.execute("SELECT password FROM users WHERE id=1 LIMIT 1;")
    connection.close()
    for row in result:
      assert REPL_PASSWORD in row["password"]
