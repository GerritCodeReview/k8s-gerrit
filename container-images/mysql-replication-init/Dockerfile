FROM ubuntu:18.04

RUN apt-get update && apt-get install -y mysql-client

COPY tools/* /var/tools/

ENTRYPOINT ["/bin/bash", "/var/tools/start"]