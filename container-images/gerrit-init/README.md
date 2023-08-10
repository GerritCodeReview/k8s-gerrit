# Gerrit replica init container image

Kubernetes init container for initializing gerrit. The python script running in
the container initializes Gerrit including the installation of configured
plugins.

## Content

* gerrit-base image

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
downloadedPlugins: []
# - name: saml
#   url: "https://example.com/saml.jar"
#   sha1: 1234
packagedPlugins: []
# - delete-projects
installAsLibrary: []
# - saml
#DEPRECATED: `pluginCache` was deprecated in favor of `pluginCacheEnabled`
# pluginCache: true
pluginCacheEnabled: false
pluginCacheDir: null
# Can be either true to use default CA certificates, false to disable SSL
# verification or a path to a custom CA certificate store.
caCertPath: true
```
