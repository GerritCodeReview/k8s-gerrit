
ARG TAG=latest
FROM gerrit-base:${TAG}

USER root
COPY housekeeper /var/housekeeper/
WORKDIR /var/housekeeper

ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
RUN apk update && \
    apk add --no-cache \
      python3 && \
    python3 -m venv $VIRTUAL_ENV && \
    python3 -m ensurepip --upgrade && \
    rm -r /usr/lib/python*/ensurepip && \
    python3 -m pip install --require-hashes -r requirements.txt --no-cache --upgrade

COPY housekeeper /var/houskeeper/

RUN mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git

USER gerrit

ENTRYPOINT ["python3", "/var/housekeeper/housekeeper.py"]
