FROM ubuntu:18.04

RUN apt-get update
RUN apt-get -y install git

# Allow sudo
RUN apt-get -y install sudo

COPY tools/* /var/tools/
