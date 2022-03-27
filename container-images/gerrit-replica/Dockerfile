FROM gerrit-base:latest

COPY tools/* /var/tools/

# Configure Gerrit as replica
RUN apk add --no-cache coreutils \\
 && git config -f /var/gerrit/etc/gerrit.config container.replica true
