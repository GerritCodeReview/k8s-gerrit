# Gerrit slave image

Image for Gerrit slave

## Content

* gerrit-base image
* mysql-driver for Gerrit

## Setup and configuration

* batch initialize Gerrit site in `/var/gerrit`
* install plugins
* install MySQL driver
* ensure gerrit is configured as gerrit slave

## Start

* starts the container via start script `/var/tools/start`
(Entrypoint is inherited from gerrit-base image)
* If configuration files (`gerrit.config`, `secret.config`, and `keystore`) are
provide in `/var/config`, they will be symlinked into the Gerrit site.
