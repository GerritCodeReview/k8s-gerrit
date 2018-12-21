# Git GC container image

Container for running `git gc`. It is meant to run as a CronJob, when used in
Kubernetes. It can also be used to run garbage collection on-demand, e.g. using
a Kubernetes Job.

## Content

* base image
* `gc-all.sh`: gc-script

## Setup and configuration

* copy tools scripts
* ensure filesystem permissions

## Start

*  execution of the provided `gc-all.sh`
