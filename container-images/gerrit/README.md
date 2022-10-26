# Gerrit image

Container image for a Gerrit instance

## Content

* the [gerrit-base](../gerrit-base/README.md) image
* `/var/tools/start`: start script

## Start

* starts Gerrit via start script `/var/tools/start` either as primary or replica
  depending on the provided `gerrit.config`
