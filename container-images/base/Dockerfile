FROM ubuntu:18.04

RUN apt-get update
RUN apt-get -y install git

# Allow remote connectivity and sudo
RUN apt-get -y install openssh-client sudo

COPY tools/* /var/tools/