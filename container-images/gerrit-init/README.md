# Gerrit slave init container image

Kubernetes init container for initializing gerrit. The python script running in
the container initializes Gerrit including the installation of configured
plugins.

## Content

* gerrit-base image

## Setup and configuration

* install python 3
* copy tool scripts

## Start

* start the container via start script `/var/tools/gerrit_init.py`

The `download_plugins.py`-script

* parses required plugins from config file
* removes unwanted plugins
* installs and updates plugins not packaged in Gerrit's war-file
* plugin files are validated using SHA1
* plugin files may optionally be cached

The `gerrit_init.py`-script

* reads configuration from gerrit.config (via `gerrit_config_parser.py`)
* initializes Gerrit

The `validate_notedb.py`-script

* validates and waits for the repository `All-Projects.git` with the refs
`refs/meta/config`.
* validates and waits for the repository `All-Users.git` with the ref
`refs/meta/config`.
