# Gerrit image

Gerrit image for a primary Gerrit instance

## Content

* Gerrit base image
* `/var/tools/start`: start script

## Start

* starts the container via start script `/var/tools/start` (definition of
Entrypoint is inherited from gerrit-base container)
* If configuration files (`gerrit.config`, `secret.config`, `replication.config`
and `keystore`) are provide in `/var/config`, they will be symlinked into the
Gerrit site.
