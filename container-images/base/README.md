# Base image

This is the base Docker image for Gerrit deployment on Kubernetes.
It is only used in the build process and not published on Dockerhub.

## Content

* Alpine Linux 3.10.0
* git
* create `gerrit`-user as a non-root user to run the applications
