FROM ubuntu:18.04

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

RUN apt-get update && \
    apt-get -y install \
      git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users
