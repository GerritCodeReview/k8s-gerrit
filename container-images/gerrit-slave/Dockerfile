FROM gerrit-base:latest

RUN java -jar /var/gerrit/bin/gerrit.war init --batch --no-auto-start --install-plugin singleusergroup -d /var/gerrit

COPY tools/* /var/tools/

# Install MySQL driver for Gerrit

RUN /var/tools/download_db_driver

RUN git config -f /var/gerrit/etc/gerrit.config container.slave true
