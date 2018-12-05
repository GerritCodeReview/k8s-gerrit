#!/usr/bin/python3
from abc import ABC, abstractmethod
from sqlalchemy import create_engine

import argparse
import os.path
import subprocess
import sys
import time

from multioptconfigparser import *

class AbstractGerritDB(ABC):

  def __init__(self, config):
    self._read_config(config)

  @abstractmethod
  def _read_config(self):
    pass

  @abstractmethod
  def _create_db_url(self):
    pass

  @abstractmethod
  def wait_for_db_server(self):
    pass

  @abstractmethod
  def wait_for_db(self):
    pass

  @abstractmethod
  def wait_for_schema(self):
    pass


class H2GerritDB(AbstractGerritDB):

  def __init__(self, config, site):
    super().__init__(config)
    self.url = self._create_db_url(site)

  def _read_config(self, config):
    self.name = config.get("database", "database", fallback="ReviewDB")

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

  def _read_config(self, config):
    self.host = config.get("database", "hostname", fallback="localhost")
    self.port = config.get("database", "port", fallback=3306)
    self.user = config.get("database", "username", fallback="")
    self.pwd = config.get("database", "password", fallback="")
    self.name = config.get("database", "database", fallback="reviewdb")

    # tables expected in Gerrit 2.12 - 2.16
    self.tables = ['changes', 'patch_sets']

    self.server_url, self.reviewdb_url = self._create_db_url()

  def _create_db_url(self):
    server_url = "mysql+pymysql://%s:%s@%s:%s" % (self.user, self.pwd, self.host, self.port)
    reviewdb_url = "%s/%s" % (server_url, self.name)
    return (server_url, reviewdb_url)

  def _connect_to_db(self, url):
    self.engine = create_engine(url, echo=True)
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
        print("%s: Still waiting for table %s..." % (time.ctime(), table), flush=True)
        time.sleep(3)
    print("%s: Schema appears to have been created!" % time.ctime())


def read_gerrit_config(gerrit_site_path):
  gerrit_config_path = gerrit_site_path + "/etc/gerrit.config"
  gerrit_secure_config_path = gerrit_site_path + "/etc/secure.config"
  gerrit_config = ConfigParserMultiOpt()
  gerrit_config.read([gerrit_config_path, gerrit_secure_config_path])
  return gerrit_config

def select_db(config, site):
  db_type = config["database"]["type"]

  if db_type.upper() == "H2":
    gerrit_db = H2GerritDB(config, site)
  elif db_type.upper() == "MYSQL":
    gerrit_db = MysqlGerritDB(config)
  else:
    print("Unknown database type.")
    sys.exit(1)

  return gerrit_db

def initialize_gerrit(gerrit_site_path, plugins):
  if plugins:
    plugin_options = ' '.join(['--install-plugin %s' % plugin for plugin in plugins])
  else:
    plugin_options = ''

  command = "java -jar /var/war/gerrit.war init --batch %s -d %s" % (
    plugin_options,
    gerrit_site_path)

  exit_code = subprocess.call(command.split(), stdout=subprocess.PIPE)

  if exit_code > 0:
    print("An error occured, when initializing Gerrit. Exit code: ", exit_code)
    sys.exit(1)

def main(gerrit_site_path, init, plugins):
  config = read_gerrit_config(gerrit_site_path)
  gerrit_db = select_db(config, gerrit_site_path)

  gerrit_db.wait_for_db_server()
  if init:
    initialize_gerrit(gerrit_site_path, plugins)
  gerrit_db.wait_for_db()
  gerrit_db.wait_for_schema()

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument("-s", "--site", help="Path to Gerrit site", dest="site",
    action="store", default="/var/gerrit", required=True)
  parser.add_argument("-i", "--initialize", help="If set, initializes Gerrit site.",
    dest="init", action="store_true", default=False)
  parser.add_argument("-p", "--plugin",
    help="Plugins to be installed. Only used, if '--initialize' is set.",
    dest="plugins", action="append", default=None)
  args = parser.parse_args()

  try:
    gerrit_site_path = args.site
    init = args.init
    plugins = args.plugins
  except Exception as e:
    print("No Gerrit site specified.")
    sys.exit(1)

  main(gerrit_site_path, init, plugins)
