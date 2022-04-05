ARG TAG=latest
FROM gerrit-base:${TAG}

COPY tools/* /var/tools/

# Configure Gerrit as replica
RUN git config -f /var/gerrit/etc/gerrit.config container.replica true
