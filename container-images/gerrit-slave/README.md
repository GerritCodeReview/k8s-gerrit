# Gerrit slave image

Gerrit base image for Gerrit slave

## Content

* gerrit-base image

## Setup and configuration

* batch initialize Gerrit site in `/var/gerrit`
* install plugins
* install MySQL driver
* ensure gerrit is configured as gerrit slave

## Start

* starts the container via start script `/var/tools/start` (inherited from gerrit-base image)