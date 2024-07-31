# Gerrit image

Container image for a Gerrit instance

## Content

* git
* curl
* openssh-keygen
* OpenJDK 11
* gerrit.war
* required gerrit plugins
* `/var/tools/start`: start script

## Setup and configuration

* install package dependencies
* create base folders for gerrit binary and gerrit configuration
* download gerrit.war from provided URL
* download gerrit plugins
* prepare filesystem permissions for gerrit user
* open ports for incoming traffic

## Start

* starts Gerrit via start script `/var/tools/start` either as primary or replica
  depending on the provided `gerrit.config`
