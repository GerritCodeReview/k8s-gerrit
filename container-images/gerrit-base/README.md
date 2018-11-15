# Gerrit base image

Gerrit base image for Gerrit master and Gerrit slave images.
It is only used in the build process and not published on Dockerhub.

## Content

* base image
* gerrit.war
* OpenJDK 8

## Setup and configuration

* create base folders for gerrit binary and gerrit configuration
* create gerrit OS user
* download gerrit.war from provided URL
* prepare filesystem permissions for gerrit user
* open ports for incoming traffic

## Start

* starts the container via start script `/var/tools/start`