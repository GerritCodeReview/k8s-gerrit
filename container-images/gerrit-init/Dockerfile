FROM gerrit-base:latest

USER root

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY tools/* /var/tools/
WORKDIR /var/tools

RUN apt-get update && \
    apt-get install -y \
      python3 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

USER gerrit

ENTRYPOINT ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
