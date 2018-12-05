# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container makes sure, that the database is initialized (currently supported:
H2 and MySQL) and initializes Gerrit including the installation of configured
core plugins.

## Content

* gerrit-base image

## Setup and configuration

* install mysql-client, python 3 and pip
* install sqlalchemy and mysql driver for python
* copy tool scripts

## Start

* verify filesystem permissions
* start the container via start script `/var/tools/start`

The start script

* removes a potentially existing old gerrit.pid file
* starts up the `gerrit-init.py`-script

The `gerrit-init.py`-script

* reads database configuration from gerrit.config
* waits for the database to start
* waits for MySQL slave database schema to arrive via database replication from
 Gerrit slave
* If configured, starts Gerrit initialization and installs configured core-plugins