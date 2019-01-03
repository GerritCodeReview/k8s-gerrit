# Base image

This is the base Docker image for Gerrit deployment on Kubernetes.
It is only used in the build process and not published on Dockerhub.

## Content

* Ubuntu 18.04
* git
* create `gerrit`-user as a non-root user to run the applications
