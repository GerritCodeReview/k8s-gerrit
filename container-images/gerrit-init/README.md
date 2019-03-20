# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container makes sure, that the database is initialized (currently supported:
H2 and MySQL) and initializes Gerrit including the installation of configured
plugins.

## Content

* gerrit-base image

## Setup and configuration

* install mysql-client, python 3, pip and pipenv
* install sqlalchemy and mysql driver for python using pipenv
* copy tool scripts

## Start

* start the container via start script `/var/tools/gerrit_init.py`

The `download_plugins.py`-script

* parses required plugins from config file
* removes unwnated plugins
* installs and updates plugins not packaged in Gerrit's war-file
* plugin files are validated using SHA1
* plugin files may optionally be cached

The `gerrit_init.py`-script

* reads configuration from gerrit.config (via `gerrit_config_parser.py`)
* waits for the database to start (via `validate_db.py`)
* initializes Gerrit

The `validate_db.py`-script

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
