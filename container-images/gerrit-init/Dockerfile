FROM gerrit-base:latest

USER root

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY tools /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3

USER gerrit

ENTRYPOINT ["/var/tools/gerrit-prepper/main.py", "-s", "/var/gerrit", "init"]
