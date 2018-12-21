FROM base:latest

# Install cron
RUN apt-get update && \
    apt-get -y install \
      cron && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY tools/* /var/tools/
COPY cron/* /var/cron/

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users && \
    mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git

VOLUME ["/var/gerrit/git"]

# Start
ENTRYPOINT ["/bin/bash", "/var/tools/start"]
