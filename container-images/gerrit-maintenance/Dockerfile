ARG TAG=latest
FROM base:${TAG}

USER root
COPY tools/requirements.txt /var/tools/requirements.txt
WORKDIR /var/tools

ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m venv $VIRTUAL_ENV && \
    python3 -m ensurepip --upgrade && \
    rm -r /usr/lib/python*/ensurepip && \
    python3 -m pip install --require-hashes -r requirements.txt --no-cache --upgrade

COPY tools /var/tools

RUN mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git

USER gerrit

ENTRYPOINT ["python3", "/var/tools/gerrit-maintenance.py"]
