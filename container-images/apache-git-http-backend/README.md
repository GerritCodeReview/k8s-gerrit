# apache-git-http-backend

The apache-git-http-backend docker image serves as receiver in git replication
from a Gerrit to a Gerrit replica.

## Content

* Apache webserver
* Apache configurations for http
* git and git-deamon for git-http-backend
* `tools/project_admin.sh`: cgi script to enable remote creation/deletion/HEAD update
  of git repositories. Compatible with replication plugin.
* `tools/start`: start script, configures and starts Apache webserver

## Setup and Configuration

* install Apache webserver, additional Apache tools and git daemon
* configure Apache
* install cgi scripts
* map volumes

## Start

* start Apache git-http backend via start script `/var/tools/start`
