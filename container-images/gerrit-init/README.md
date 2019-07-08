# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container makes sure, that the database is initialized (currently supported:
H2 and MySQL) and initializes Gerrit including the installation of configured
core plugins.

## Content

* gerrit-base image

## Setup and configuration

The container image is build in a multistage build.

The build-image:
* install python 3, pip and pipenv
* install pyinstaller, sqlalchemy and mysql driver for python using pipenv
* freeze the CLI tool

The gerrit-init image:
* install mysql-client
* copy CLI-executable from build-image

## Start

* start the container via start script `./gerrit-prepper init`

The `gerrit-prepper init`-command

* reads configuration from gerrit.config
* waits for the database to start
* initializes Gerrit

The `gerrit-prepper validate-db`-command

* reads database configuration from gerrit.config
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
