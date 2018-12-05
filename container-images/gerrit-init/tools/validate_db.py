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

from abc import ABC, abstractmethod
from sqlalchemy import create_engine

import argparse
import os.path
import sys
import time

from git_config_parser import GitConfigParser

class AbstractGerritDB(ABC):

  def __init__(self, config, secure_config):
    self._read_config(config, secure_config)

  @abstractmethod
  def _read_config(self, config, secure_config):
    """
    Should read all required configuration values.
    """
    pass

  @abstractmethod
  def _create_db_url(self):
    """
    Should create a URL with which the database can be reached.
    """
    pass

  @abstractmethod
  def wait_for_db_server(self):
    """
    Should try to connect to the database server and wait until a connection
    is achieved.
    """
    pass

  @abstractmethod
  def wait_for_db(self):
    """
    Should check whether a database with the name configured for the ReviewDB
    exists on the database server and wait for its creation.
    """
    pass

  @abstractmethod
  def wait_for_schema(self):
    """
    Should check whether the schema of the ReviewDBwas created and wait for its
    creation.
    """
    pass


class H2GerritDB(AbstractGerritDB):

  def __init__(self, config, secure_config, site):
    super().__init__(config, secure_config)
    self.url = self._create_db_url(site)

  def _read_config(self, config, secure_config):
    self.name = config.get("database.database", default="ReviewDB")

  def _create_db_url(self, site):
    suffix = '.h2.db'
    if os.path.isabs(self.name):
      if self.name.endswith(suffix):
        return self.name
      else:
        return self.name + suffix
    else:
      return os.path.join(site, "db", self.name) + suffix

  def wait_for_db_server(self):
    # Not required. H2 is a file-based database.
    pass

  def wait_for_db(self):
    print("%s: Waiting for database to be available..." % time.ctime())
    while not os.path.exists(self.url):
      time.sleep(3)
      print("%s: Still waiting..." % time.ctime(), flush=True)
    print("%s: Found it!" % time.ctime())

  def wait_for_schema(self):
    # Since no replication of a H2 databas eis implemented yet, this test is not
    # needed, becaus ethe schema is created by Gerrit.
    pass


class MysqlGerritDB(AbstractGerritDB):

  def __init__(self, config, secure_config):
    super().__init__(config, secure_config)

    # tables expected in Gerrit 2.12 - 2.16
    self.tables = ['changes', 'patch_sets']
    self.server_url, self.reviewdb_url = self._create_db_url()

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
    print("%s: Waiting for database server connection..." % time.ctime())
    while not hasattr(self, 'connection') or self.connection.closed:
      try:
        self._connect_to_db(self.server_url)
        continue
      except:
        print("%s: Still waiting..." % time.ctime(), flush=True)
        time.sleep(3)
    self.connection.close()
    print("%s: Connection successful!" % time.ctime())

  def wait_for_db(self):
    print("%s: Waiting for database to be available..." % time.ctime())
    self.connection.close()
    while not hasattr(self, 'connection') or self.connection.closed:
      try:
        self._connect_to_db(self.reviewdb_url)
        continue
      except:
        print("%s: Still waiting..." % time.ctime(), flush=True)
        time.sleep(3)
    self.connection.close()
    print("%s: Found it!" % time.ctime())

  def wait_for_schema(self):
    print("%s: Waiting for database schema to be created..." % time.ctime())
    for table in self.tables:
      while not self.engine.dialect.has_table(self.engine, table):
        print("%s: Still waiting for table %s..." % (
            time.ctime(),
            table),
          flush=True)
        time.sleep(3)
    print("%s: Schema appears to have been created!" % time.ctime())

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
    print("Unknown database type.")
    sys.exit(1)

  return gerrit_db

def validate_db(gerrit_site_path):
  gerrit_db = select_db(gerrit_site_path)

  gerrit_db.wait_for_db_server()
  gerrit_db.wait_for_db()
  gerrit_db.wait_for_schema()

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
