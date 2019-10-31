FROM gerrit-base:latest

USER root

COPY tools/* /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3

USER gerrit

ENTRYPOINT ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
