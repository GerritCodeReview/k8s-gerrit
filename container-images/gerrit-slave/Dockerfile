FROM gerrit-base:latest

COPY tools/* /var/tools/

# Initialize Gerrit site, install MySQL driver for Gerrit and configure Gerrit
# as slave
RUN /var/tools/download_db_driver && \
    git config -f /var/gerrit/etc/gerrit.config container.slave true
