# Apache git-http backend

The Apache git-http backend docker image serves as receiver in git replication from a Gerrit master to a Gerrit slave.

## Content

* base image
* Apache webserver
* Apache configurations for http and https
* git (via base image)
* `tools/create_repo.sh`: cgi script to enable remote creation of new git repository over http. This is triggered by the Gerrit replication plugin if a new repository on the Gerrit master does not yet exist in a Gerrit slave
* `tools/start`: start script for Kubernetes, configures and starts Apache webserver
* `start`: start script for testing image using Docker

## Setup and Configuration

* install Apache webserver
* configure Apache for http and/or https
* install cgi script
* open ports for incoming traffic
* create gerrit OS user
* map volumes

## Start

* verify filesystem permissions 
* start Apache git-http backend  via start script `/var/tools/start`