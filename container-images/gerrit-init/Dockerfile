FROM gerrit-base:latest

USER root

COPY dependencies/* /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m ensurepip && \
    rm -r /usr/lib/python*/ensurepip && \
    pip3 install --no-cache --upgrade pip setuptools wheel pipenv && \
    pipenv install --python 3.7.3 --system

COPY tools /var/tools/
COPY config/* /var/config/

USER gerrit

ENTRYPOINT ["/var/tools/gerrit-initializer/main.py"]
