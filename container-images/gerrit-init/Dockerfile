FROM gerrit-base:latest

USER root

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY dependencies/* /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3 && \
    pip3 install pipenv && \
    pipenv install --python 3.7 --system

COPY tools/* /var/tools/

USER gerrit

ENTRYPOINT ["/var/tools/gerrit_init.py", "-s", "/var/gerrit"]
