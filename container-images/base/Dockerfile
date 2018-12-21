FROM ubuntu:18.04

RUN apt-get update
RUN apt-get -y install git

# Allow remote connectivity and sudo
RUN apt-get -y install openssh-client sudo

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users

COPY tools/* /var/tools/
