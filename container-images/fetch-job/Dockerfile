ARG TAG=latest
FROM base:${TAG}

USER root

COPY dependencies/* /var/tools/
COPY requirements.txt /var/tools/
WORKDIR /var/tools

ENV VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
RUN apk update && \
    apk add --no-cache python3 && \
    python3 -m venv $VIRTUAL_ENV && \
    python3 -m ensurepip --upgrade && \
    rm -r /usr/lib/python*/ensurepip && \
    python3 -m pip install --require-hashes -r requirements.txt --no-cache --upgrade && \
    python3 -m pipenv install --python 3.12 --system

COPY tools/* /var/tools/

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["python3", "/var/tools/fetch-job.py"]
