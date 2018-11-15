# Gerrit slave init container image

Kubernetes init container for initializing a gerrit slave. Currently also
used to initialize gerrit master using a different Entrypoint, will be cleaned
up in a future change.

## Content

* gerrit-slave image

## Setup and configuration

* install mysql-client
* copy tool scripts

## Start

* verify filesystem permissions
* start the container via start script `/var/tools/start` (definition of
 Entrypoint is inherited from gerrit-base image)

The start script

* reads database configuration from gerrit.config
* waits for the database to start
* waits for All-Projects.git and All-Users.git to arrive via replication via
 apache-git-http-backend from Gerrit master
* waits for MySQL slave database schema to arrive via database replication from
 Gerrit slave