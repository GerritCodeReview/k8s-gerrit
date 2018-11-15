# apache-git-http-backend

The apache-git-http-backend docker image serves as receiver in git replication
from a Gerrit master to a Gerrit slave.

## Content

* base image
* Apache webserver
* Apache configurations for http and https
* git (via base image)
* `tools/create_repo.sh`: cgi script to enable remote creation of new git
 repository over http. This is triggered by the Gerrit replication plugin
 if a new repository on the Gerrit master does not yet exist in a Gerrit slave,
 a corresponding
 [change for the replication plugin](https://gerrit-review.googlesource.com/c/plugins/replication/+/199900)
 enabling repository creation via http is still in review for master and will be
 downported to 2.16
* `tools/start`: start script, configures and starts Apache
 webserver
* `start`: start script for testing image using Docker

## Setup and Configuration

* install Apache webserver
* configure Apache for http and/or https
* install cgi script
* open ports for incoming traffic
* create gerrit OS user
* map volumes

## Start

* verify filesystem permissions. In Kubernetes this is done using a
 [SecurityContext](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/).
 It is used to set the filesystem group of mounted volumes to 100 (users),
 which is used by the gerrit-user in the containers. Thereby it is ensured
 that the volumes have rw-permissions for the gerrit-user.
* start Apache git-http backend  via start script `/var/tools/start`