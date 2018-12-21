FROM base:latest

# Install cron
RUN apt-get update
RUN apt-get -y install cron

COPY tools/* /var/tools/
COPY cron/* /var/cron/
RUN mkdir -p /var/log/git

RUN chown gerrit:users /var/log/git

VOLUME ["/var/gerrit/git"]

# Start
ENTRYPOINT ["/bin/bash", "/var/tools/start"]
