FROM gerrit-base:latest

RUN java -jar /var/gerrit/bin/gerrit.war init --batch --no-auto-start --install-plugin singleusergroup -d /var/gerrit

# Install MySQL driver for Gerrit
# TODO: Does not work for Gerrit >= 2.14, since the file structure in gerrit.war
# changed
RUN unzip -xOf /var/gerrit/bin/gerrit.war WEB-INF/lib/gerrit-pgm-init.jar -d /tmp && \
    LIBRARY_CONFIG_PATH="com/google/gerrit/pgm/init/libraries.config" && \
    unzip -xOf /tmp/WEB-INF/lib/gerrit-pgm-init.jar ${LIBRARY_CONFIG_PATH} -d /tmp && \
    ( cd /var/gerrit/lib && \
      curl -LO $(git config --file /tmp/${LIBRARY_CONFIG_PATH} --get library.mysqlDriver.url) ) && \
    git config --file /tmp/${LIBRARY_CONFIG_PATH} --get library.mysqlDriver.sha1  && \
    echo "$(git config --file /tmp/${LIBRARY_CONFIG_PATH} --get library.mysqlDriver.sha1)  $(find /var/gerrit/lib -name 'mysql-connector-java-*.jar')" | shasum -c - && \
    rm -rf /tmp/WEB-INF /tmp/${LIBRARY_CONFIG_PATH}

RUN git config -f /var/gerrit/etc/gerrit.config container.slave true

COPY tools/* /var/tools/
