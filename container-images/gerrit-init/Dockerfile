ARG TAG=latest
FROM gerrit-base:${TAG}

USER root

COPY dependencies/* /var/tools/
COPY requirements.txt /var/tools/
WORKDIR /var/tools

RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m ensurepip && \
    rm -r /usr/lib/python*/ensurepip && \
    # follow https://til.simonwillison.net/python/pip-tools to update hashes
    pip3 install --require-hashes -r requirements.txt --no-cache --upgrade && \
    pipenv install --python 3.11 --system

COPY tools /var/tools/
COPY config/* /var/config/

RUN mkdir -p /var/mnt/git \
  && chown -R gerrit:users /var/mnt

USER gerrit

ENTRYPOINT ["python3", "/var/tools/gerrit-initializer"]
CMD ["-s", "/var/gerrit", "-c", "/var/config/gerrit-init.yaml", "init"]
