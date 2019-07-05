# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container makes sure, that the database is initialized (currently supported:
H2 and MySQL) and initializes Gerrit including the installation of configured
core plugins.

## Content

* gerrit-base image

## Setup and configuration

* install mysql-client, python 3, pip and pipenv
* install sqlalchemy and mysql driver for python using pipenv
* copy tool scripts

## Start

* start the container via start script `/var/tools/gerrit-prepper/main.py init`

The `main.py init`-command

* reads configuration from gerrit.config (via `gerrit_config_parser.py`)
* waits for the database to start (via `validate_db.py`)
* initializes Gerrit

The `main.py validate-db`-command

* reads database configuration from gerrit.config (via `gerrit_config_parser.py`)
* waits for the database to start
* waits for the reviewdb database
+ waits for some selected tables to ensure that the schema is initialized

## How to install/update python packages in container

* Python 3.6 is required
* Install `pipenv`
* Navigate to `./container-images/gerrit-init/tools`
* Run `pipenv install <package>`

This will update the `Pipfile` and `Pipfile.lock`, which will be copied into the
container image, when the container image is built.
