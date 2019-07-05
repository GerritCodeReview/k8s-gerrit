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
import time

from sqlalchemy import create_engine
from sqlalchemy.exc import SQLAlchemyError

import pytest

class MySQLContainer():

  def __init__(self, docker_client, docker_network, mysql_config):
    self.docker_client = docker_client
    self.docker_network = docker_network
    self.mysql_config = mysql_config

    self.mysql_container = None

    self._start_mysql_container()

  def connect(self):
    engine = create_engine(
      "mysql+pymysql://root:%s@localhost:%s" % (
        self.mysql_config["MYSQL_ROOT_PASSWORD"],
        self.mysql_config["MYSQL_PORT"]))
    return engine.connect()

  def _wait_for_db_connection(self):
    connection = None
    while connection is None:
      try:
        connection = self.connect()
        continue
      except SQLAlchemyError:
        time.sleep(1)
    connection.close()

  def _start_mysql_container(self):
    self.mysql_container = self.docker_client.containers.run(
      image="mysql:5.5.61",
      environment={
        "MYSQL_ROOT_PASSWORD": self.mysql_config["MYSQL_ROOT_PASSWORD"],
        "MYSQL_DATABASE": "reviewdb"
      },
      ports={
        "3306": self.mysql_config["MYSQL_PORT"]
      },
      network=self.docker_network.name,
      name=self.mysql_config["MYSQL_HOST"],
      detach=True,
      auto_remove=True
    )

    self._wait_for_db_connection()

  def stop_mysql_container(self):
    self.mysql_container.stop(timeout=1)

@pytest.fixture(scope="session")
def mysql_container_factory():
  def get_mysql_container(docker_client, docker_network, mysql_config):
    return MySQLContainer(docker_client, docker_network, mysql_config)

  return get_mysql_container


class GerritContainer():

  def __init__(self, docker_client, docker_network, tmp_dir, image,
               configs, port):
    self.docker_client = docker_client
    self.docker_network = docker_network
    self.tmp_dir = tmp_dir
    self.image = image
    self.configs = configs
    self.port = port

    self.gerrit_container = None

    self._start_gerrit_container()

  def _create_config_files(self):
    tmp_config_dir = os.path.join(self.tmp_dir, "configs")
    if not os.path.isdir(tmp_config_dir):
      os.mkdir(tmp_config_dir)
    config_paths = {}
    for filename, content in self.configs.items():
      gerrit_config_file = os.path.join(tmp_config_dir, filename)
      with open(gerrit_config_file, "w") as config_file:
        config_file.write(content)
      config_paths[filename] = gerrit_config_file
    return config_paths

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

@pytest.fixture(scope="session")
def gerrit_container_factory():
  def get_gerrit_container(docker_client, docker_network, tmp_dir, image,
                           gerrit_config, port):
    return GerritContainer(docker_client, docker_network, tmp_dir, image,
                           gerrit_config, port)

  return get_gerrit_container
