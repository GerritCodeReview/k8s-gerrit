FROM ubuntu:18.04

RUN apt-get update && \
    apt-get -y install \
      git \
      openssh-client && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users

COPY tools/* /var/tools/
