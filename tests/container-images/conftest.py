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

import pytest
import time

class MySQLContainer():

  def __init__(self, docker_client, docker_network, host, port, root_pwd):
    self.docker_client = docker_client
    self.docker_network = docker_network
    self.host = host
    self.port = port
    self.root_pwd = root_pwd

    self.mysql_container = None

    self._start_mysql_container()

  def connect(self):
    engine = create_engine("mysql+pymysql://root:%s@localhost:%s" % (
          self.root_pwd, self.port))
    return engine.connect()

  def _wait_for_db_connection(self):
    connection = None
    while connection is None:
      try:
        connection = self.connect()
        continue
      except:
        time.sleep(1)
    connection.close()

  def _start_mysql_container(self):
    self.mysql_container = self.docker_client.containers.run(
      image = "mysql:5.5.61",
      environment = {
        "MYSQL_ROOT_PASSWORD": self.root_pwd,
        "MYSQL_DATABASE": "reviewdb"
      },
      ports = {
        "3306": self.port
      },
      network = self.docker_network.name,
      name = self.host,
      detach = True,
      auto_remove = True
    )

    self._wait_for_db_connection()

  def stop_mysql_container(self):
    self.mysql_container.stop(timeout=1)

@pytest.fixture(scope="session")
def mysql_container_factory():
  def get_mysql_container(docker_client, docker_network, host, port, root_pwd):
    return MySQLContainer(docker_client, docker_network, host, port, root_pwd)

  return get_mysql_container
