# Git GC container image

Container for running `git gc`. The container itself runs a cron for use when
running it using Docker. In Kubernetes we use a CronJob, thus the entrypoint
is overwritten, when used in Kubernetes, to not run cron via `/var/tools/start`,
but directly run `gc-all.sh` in Kubernetes CronJob.

## Content

* base image
* cron
* `gc-all.sh`: gc-script

## Setup and configuration

* install cron
* copy tools scripts
* install crontab
* create gerrit OS user
* ensure filesystem permissions

## Start

* start the container via start script `/var/tools/start`

The start script

* starts cron which schedules the execution of the provided `gc-all.sh`
* ensures file system permissions