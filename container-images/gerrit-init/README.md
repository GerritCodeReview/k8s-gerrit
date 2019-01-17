# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container initializes Gerrit including the installation of configured
core plugins.

## Content

* gerrit-base image

## Setup and configuration

* install python 3
* copy tool scripts

## Start

* start the container via start script `/var/tools/gerrit_init.py`

The `gerrit_init.py`-script

* reads configuration from gerrit.config (via `gerrit_config_parser.py`)
* initializes Gerrit
