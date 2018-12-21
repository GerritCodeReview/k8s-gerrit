FROM gerrit-base:latest

COPY tools/* /var/tools/

# Initialize Gerrit site, install MySQL driver for Gerrit and configure Gerrit
# as slave
RUN java -jar /var/gerrit/bin/gerrit.war init \
      --batch \
      --no-auto-start \
      --install-plugin singleusergroup \
      -d /var/gerrit && \
    /var/tools/download_db_driver && \
    git config -f /var/gerrit/etc/gerrit.config container.slave true
