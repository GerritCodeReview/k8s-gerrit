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

RUN mkdir -p /var/mnt/git \
  && mkdir -p /var/mnt/logs \
  && chown -R gerrit:users /var/mnt

USER gerrit

ENTRYPOINT ["python3", "/var/tools/gerrit-initializer"]
CMD ["-s", "/var/gerrit", "-c", "/var/config/gerrit-init.yaml", "init"]
