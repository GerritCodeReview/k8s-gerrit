FROM gerrit-base:latest

USER root

RUN apt-get update && \
    apt-get install -y \
      mysql-client && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY tools/* /var/tools/

ENV TEST_MODE=

USER gerrit

ENTRYPOINT ["/bin/bash", "-c", "/var/tools/verify_fs_permissions && /var/tools/start"]
