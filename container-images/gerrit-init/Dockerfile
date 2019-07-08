FROM python:3.7.3-stretch AS build

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY tools /var/tools/
WORKDIR /var/tools

RUN apt-get update && \
    apt-get install -y \
      mysql-client \
      python3 \
      python3-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    pip3 install pipenv && \
    pipenv install --system --dev

RUN pyinstaller gerrit-prepper.spec

################################################################################

FROM gerrit-base:latest

USER root

RUN apt-get update && \
    apt-get install -y \
      mysql-client && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /var/tools/dist /var/tools
WORKDIR /var/tools/gerrit-prepper

USER gerrit

ENTRYPOINT ["./gerrit-prepper", "-s", "/var/gerrit", "init"]
