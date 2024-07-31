# Gerrit replica init container image

Kubernetes init container for initializing gerrit. The python script running in
the container initializes Gerrit including the installation of configured
plugins.

## Content

* git
* pyhton3

## Setup and configuration

* install python 3
* copy tool scripts

## Start

* start the container via start script `python3 /var/tools/gerrit-initializer init`

The `main.py init`-command

* reads configuration from gerrit.config (via `gerrit_config_parser.py`)
* initializes Gerrit

The `main.py validate_notedb`-command

* validates and waits for the repository `All-Projects.git` with the refs
`refs/meta/config`.
* validates and waits for the repository `All-Users.git` with the ref
`refs/meta/config`.

## Configuration

The configuration format looks as follows:

```yaml
plugins: []
# A plugin packaged in the gerrit.war-file
# - name: download-commands

# A plugin packaged in the gerrit.war-file that will also be installed as a
# lib
# - name: replication
#   installAsLibrary: true

# A plugin that will be downloaded on startup
# - name: delete-project
#   url: https://example.com/gerrit-plugins/delete-project.jar
#   sha1:
#   installAsLibrary: false
libs: []
# A lib that will be downloaded on startup
# - name: global-refdb
#   url: https://example.com/gerrit-plugins/global-refdb.jar
#   sha1:
#DEPRECATED: `pluginCache` was deprecated in favor of `pluginCacheEnabled`
# pluginCache: true
pluginCacheEnabled: false
pluginCacheDir: null
# Can be either true to use default CA certificates, false to disable SSL
# verification or a path to a custom CA certificate store.
caCertPath: true
highAvailability: false
```
