FROM gerrit-base:latest

USER root

RUN apt-get update && \
    apt-get install -y mysql-client python3 python3-pip

RUN pip3 install sqlalchemy PyMySQL

COPY tools/* /var/tools/

ENV TEST_MODE=

USER gerrit

ENTRYPOINT ["/bin/bash", "-c", "/var/tools/verify_fs_permissions && /var/tools/start"]