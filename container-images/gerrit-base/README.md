# Gerrit base image

Gerrit base image for Gerrit and Gerrit replica images.
It is only used in the build process and not published on Dockerhub.

## Content

* base image
* openssh-keygen
* OpenJDK 11
* gerrit.war

## Setup and configuration

* install package dependencies
* create base folders for gerrit binary and gerrit configuration
* download gerrit.war from provided URL
* prepare filesystem permissions for gerrit user
* open ports for incoming traffic
* initialize default Gerrit site

## Start

* starts the container via start script `/var/tools/start`
