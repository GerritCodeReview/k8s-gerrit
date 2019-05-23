#!/usr/bin/python3

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

import argparse
import os.path
import sys
import time

from abc import ABC, abstractmethod
from sqlalchemy import create_engine
from sqlalchemy.exc import SQLAlchemyError

from git_config_parser import GitConfigParser
from log import get_logger

LOG = get_logger()

class AbstractGerritDB(ABC):

  def __init__(self, config, secure_config):
    self._read_config(config, secure_config)

  @abstractmethod
  def _read_config(self, config, secure_config):
    """
    Read all required configuration values.
    """

  @abstractmethod
  def _create_db_url(self):
    """
    Create a URL with which the database can be reached.
    """

  @abstractmethod
  def wait_for_db_server(self):
    """
    Wait until a connection with the database server is achieved.
    """

  @abstractmethod
  def wait_for_db(self):
    """
    Check whether a database with the name configured for the ReviewDB
    exists on the database server and wait for its creation.
    """

  @abstractmethod
  def wait_for_schema(self):
    """
    Check whether the schema of the ReviewDBwas created and wait for its
    creation.
    """


class H2GerritDB(AbstractGerritDB):

  def __init__(self, config, secure_config, site):
    super().__init__(config, secure_config)
    self.site = site
    self.url = self._create_db_url()

  def _read_config(self, config, secure_config):
    self.name = config.get("database.database", default="ReviewDB")

  def _create_db_url(self):
    suffix = '.h2.db'
    if os.path.isabs(self.name):
      if self.name.endswith(suffix):
        return self.name
      else:
        return self.name + suffix
    else:
      return os.path.join(self.site, "db", self.name) + suffix

  def wait_for_db_server(self):
    # Not required. H2 is an embedded database.
    pass

  def wait_for_db(self):
    LOG.info("Waiting for database to be available...")
    while not os.path.exists(self.url):
      time.sleep(3)
      LOG.info("Still waiting...")
    LOG.info("Found it!")

  def wait_for_schema(self):
    # Since no replication of a H2 databas is implemented yet, this test is not
    # needed, because the schema is created by Gerrit.
    pass


class MysqlGerritDB(AbstractGerritDB):

  def __init__(self, config, secure_config):
    super().__init__(config, secure_config)

    # tables expected in Gerrit 2.12 - 2.16
    self.tables = ['changes', 'patch_sets']
    self.server_url, self.reviewdb_url = self._create_db_url()

    self.engine = None
    self.connection = None

  def _read_config(self, config, secure_config):
    self.host = config.get("database.hostname", default="localhost")
    self.port = config.get("database.port", default="3306")
    self.name = config.get("database.database", default="reviewdb")
    self.user = secure_config.get("database.username", default="")
    self.pwd = secure_config.get("database.password", default="")

  def _create_db_url(self):
    server_url = "mysql+pymysql://%s:%s@%s:%s" % (
      self.user,
      self.pwd,
      self.host,
      self.port)
    reviewdb_url = "%s/%s" % (server_url, self.name)
    return (server_url, reviewdb_url)

  def _connect_to_db(self, url):
    self.engine = create_engine(url)
    self.connection = self.engine.connect()

  def wait_for_db_server(self):
    LOG.info("Waiting for database server connection...")
    while not self.connection or self.connection.closed:
      try:
        self._connect_to_db(self.server_url)
        continue
      except SQLAlchemyError:
        LOG.info("Still waiting...")
        time.sleep(3)
    self.connection.close()
    LOG.info("Connection successful!")

  def wait_for_db(self):
    LOG.info("Waiting for database to be available...")
    self.connection.close()
    while not self.connection or self.connection.closed:
      try:
        self._connect_to_db(self.reviewdb_url)
        continue
      except SQLAlchemyError:
        LOG.info("Still waiting...")
        time.sleep(3)
    self.connection.close()
    LOG.info("Found it!")

  def wait_for_schema(self):
    LOG.info("Waiting for database schema to be created...")
    for table in self.tables:
      while not self.engine.dialect.has_table(self.engine, table):
        LOG.info("Still waiting for table %s..." % table)
        time.sleep(3)
    LOG.info("Schema appears to have been created!")

def select_db(gerrit_site_path):
  gerrit_config_path = os.path.join(gerrit_site_path, "etc/gerrit.config")
  config = GitConfigParser(gerrit_config_path)

  gerrit_secure_config_path = os.path.join(gerrit_site_path, "etc/secure.config")
  secure_config = GitConfigParser(gerrit_secure_config_path)

  db_type = config.get("database.type")

  if db_type.upper() == "H2":
    gerrit_db = H2GerritDB(config, secure_config, gerrit_site_path)
  elif db_type.upper() == "MYSQL":
    gerrit_db = MysqlGerritDB(config, secure_config)
  else:
    LOG.error("Unknown database type.")
    sys.exit(1)

  return gerrit_db

def validate_db(gerrit_site_path):
  gerrit_db = select_db(gerrit_site_path)

  gerrit_db.wait_for_db_server()
  gerrit_db.wait_for_db()
  gerrit_db.wait_for_schema()

# pylint: disable=C0103
if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
    "-s",
    "--site",
    help="Path to Gerrit site",
    dest="site",
    action="store",
    default="/var/gerrit",
    required=True)
  args = parser.parse_args()

  validate_db(args.site)
