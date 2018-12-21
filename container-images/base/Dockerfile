FROM ubuntu:18.04

RUN apt-get update && \
    apt-get -y install \
      git \
      openssh-client \
      sudo && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY tools/* /var/tools/
