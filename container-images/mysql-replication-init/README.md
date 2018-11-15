# MySQL slave init container image

Kubernetes init container for initializing a MySQL slave

## Content

* Ubuntu 18.04

## Setup and configuration

* install mysql-client
* copy tool scripts

## Start

* start the container via start script `/var/tools/start`

The start script

* waits for database dump file from MySQL master to be copied into the container.
 The path to which the dump-file has to be copied, can be configured by setting the
 environment variable $FILEPATH (default: `/var/data/db/master_dump.sql`).
 The file can be copied into the container using `kubectl cp` or `docker cp`.
* imports the database dump from MySQL master into MySQL slave
* configures the MySQL slave
* starts the MySQL slave