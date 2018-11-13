FROM ubuntu:18.04

COPY apt.conf /etc/apt/apt.conf

RUN apt-get update
RUN apt-get -y install git

# Allow remote connectivity and sudo
RUN apt-get -y install openssh-client sudo

COPY tools/* /var/tools/