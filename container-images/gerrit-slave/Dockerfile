FROM gerrit-base:latest

COPY tools/* /var/tools/

# Install MySQL driver for Gerrit

RUN /var/tools/download_db_driver

RUN git config -f /var/gerrit/etc/gerrit.config container.slave true
