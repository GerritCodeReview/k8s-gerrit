FROM gerrit-base:latest

USER root

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY dependencies/* /var/tools/
WORKDIR /var/tools

RUN apt-get update && \
    apt-get install -y \
      mysql-client \
      python3 \
      python3-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    pip3 install pipenv && \
    pipenv install --system

COPY tools/* /var/tools/

USER gerrit

ENTRYPOINT ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
