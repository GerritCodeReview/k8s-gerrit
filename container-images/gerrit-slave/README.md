# Gerrit slave image

Image for Gerrit slave

## Content

* gerrit-base image

## Setup and configuration

* copy entrypoint scripts to image
* ensure gerrit is configured as gerrit slave

## Start

* starts the container via start script `/var/tools/start`
* If configuration files (`gerrit.config`, `secret.config`, and `keystore`) are
provide in `/var/config`, they will be symlinked into the Gerrit site.
