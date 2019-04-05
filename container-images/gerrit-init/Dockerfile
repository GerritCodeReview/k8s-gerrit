FROM gerrit-base:latest

USER root

RUN apt-get update && \
    apt-get install -y \
      mysql-client \
      python3 \
      python3-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    pip3 install \
      PyMySQL \
      requests \
      sqlalchemy

COPY tools/* /var/tools/

USER gerrit

ENTRYPOINT ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
