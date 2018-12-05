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

* start the container via start script `/var/tools/gerrit_init.py`

The `gerrit_init.py`-script

* reads configuration from gerrit.config (via `read_gerrit_config.py`)
* waits for the database to start (via `validate_db.py`)
* initializes Gerrit

The `validate_db.py`-script

* reads database configuration from gerrit.config (via `read_gerrit_config.py`)
* waits for the database to start
* waits for the reviewdb database
+ waits for some selected tables to ensure that the schema is initialized
