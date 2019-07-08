FROM python:3.7-alpine3.10 AS build

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY dependencies /var/tools
WORKDIR /var/tools

RUN apk --no-cache add \
      gcc \
      musl-dev \
      zlib-dev && \
    pip3 install pipenv && \
    pipenv install --python 3.7 --system --dev

COPY tools /var/tools/

RUN cxfreeze gerrit-initializer/gerrit_initializer.py \
      --target-dir dist \
      --target-name gerrit-initializer

################################################################################

FROM gerrit-base:latest

USER root

RUN apk --no-cache add python3

COPY --from=build /var/tools/dist /var/tools
COPY config/* /var/config/

USER gerrit
WORKDIR /var/tools

ENTRYPOINT ["./gerrit-initializer"]
