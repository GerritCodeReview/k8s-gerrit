FROM base:latest

# Install cron
RUN apt-get update
RUN apt-get -y install cron

COPY tools/* /var/tools/
COPY cron/* /var/cron/
RUN mkdir -p /var/log/git

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users
RUN chown gerrit:users /var/log/git

VOLUME ["/var/gerrit/git"]

# Start
ENTRYPOINT ["/bin/bash", "/var/tools/start"]
