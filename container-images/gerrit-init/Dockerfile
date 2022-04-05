ARG TAG=latest
FROM gerrit-base:${TAG}

USER root

COPY dependencies/* /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m ensurepip && \
    rm -r /usr/lib/python*/ensurepip && \
    pip3 install --no-cache --upgrade pip setuptools wheel pipenv && \
    pipenv install --python 3.9 --system

COPY tools /var/tools/
COPY config/* /var/config/

USER gerrit

ENTRYPOINT ["python3", "/var/tools/gerrit-initializer"]
CMD ["-s", "/var/gerrit", "-c", "/var/config/default.config.yaml", "init"]
