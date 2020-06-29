# apache-git-http-backend

The apache-git-http-backend docker image serves as receiver in git replication
from a Gerrit to a Gerrit replica.

## Content

* base image
* Apache webserver
* Apache configurations for http and https
* git (via base image) and git-deamon for git-http-backend
* `tools/create_repo.sh`: cgi script to enable remote creation of new git
 repository over http. This is triggered by the Gerrit replication plugin
 if a new repository on the Gerrit does not yet exist in a Gerrit replica,
 a corresponding
 [change for the replication plugin](https://gerrit-review.googlesource.com/c/plugins/replication/+/199900)
 enabling repository creation via http is still in review for master and will be
 downported to 2.16
* `tools/start`: start script, configures and starts Apache
 webserver

## Setup and Configuration

* install Apache webserver, additional Apache tools and git daemon
* configure Apache
* install cgi script
* map volumes

## Start

* start Apache git-http backend via start script `/var/tools/start`
