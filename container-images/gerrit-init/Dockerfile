ARG TAG=latest
FROM gerrit-base:${TAG}

USER root

COPY dependencies/* /var/tools/
COPY requirements.txt /var/tools/
WORKDIR /var/tools

ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m venv $VIRTUAL_ENV && \
    python3 -m ensurepip --upgrade && \
    rm -r /usr/lib/python*/ensurepip && \
    # follow https://til.simonwillison.net/python/pip-tools to update hashes
    python3 -m pip install --require-hashes -r requirements.txt --no-cache --upgrade && \
    python3 -m pipenv install --python 3.12 --system

COPY tools /var/tools/
COPY config/* /var/config/

RUN mkdir -p /var/mnt/git \
  && chown -R gerrit:users /var/mnt

USER gerrit

ENTRYPOINT ["python3", "/var/tools/gerrit-initializer"]
CMD ["-c", "/var/config/gerrit-init.yaml", "init"]
