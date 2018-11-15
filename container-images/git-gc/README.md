# Git GC container image

Container for running `git gc` as a Kubernetes job

## Content

* base image

## Setup and configuration

* install cron
* copy tools scripts
* install crontab
* create gerrit OS user
* ensure filesystem permissions

## Start

* start the container via start script `/var/tools/start`

The start script

* starts cron
* ensures file system permissions