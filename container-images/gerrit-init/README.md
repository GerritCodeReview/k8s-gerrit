# Gerrit replica init container image

Kubernetes init container for initializing gerrit. The python script running in
the container initializes Gerrit including the installation of configured
plugins.

## Content

* gerrit-base image

## Setup and configuration

The container image is build in a multistage build.

The build-image:
* install python 3, pip and pipenv
* install pyinstaller for python using pipenv
* freeze the CLI tool

The gerrit-init image:
* copy CLI-executable from build-image

## Start

* start the container via start script `./gerrit-initializer init`

The `gerrit-initializer init`-command

* reads configuration from gerrit.config
* initializes Gerrit

The `gerrit-initializer validate-notedb`-command

* validates and waits for the repository `All-Projects.git` with the refs
`refs/meta/config`.
* validates and waits for the repository `All-Users.git` with the ref
`refs/meta/config`.

## How to install/update python packages in container

* Python 3.7 is required
* Install `pipenv`
* Navigate to `./container-images/gerrit-init/tools`
* Run `pipenv install <package>`

This will update the `Pipfile` and `Pipfile.lock`, which will be copied into the
container image, when the container image is built.
