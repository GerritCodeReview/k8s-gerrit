FROM gerrit-base:latest

COPY tools/* /var/tools/

# Configure Gerrit as slave
RUN git config -f /var/gerrit/etc/gerrit.config container.slave true
