ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache \
      coreutils \
      curl \
      openssh-keygen \
      openjdk21

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/gerrit/plugins && \
    mkdir -p /var/plugins && \
    mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.11.0-rc1.war
RUN curl -f -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-healthcheck-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -f -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/module-global-refdb-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/global-refdb/global-refdb.jar
RUN curl -f -k -o /var/plugins/global-refdb.jar ${GLOBAL_REFDB_URL}

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-high-availability-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
RUN curl -f -k -o /var/plugins/high-availability.jar ${HA_JAR_URL}

# Download zookeeper-refdb plugin
ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-zookeeper-refdb-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
RUN curl -f -k -o /var/plugins/zookeeper-refdb.jar ${ZOOKEEPER_REFDB_URL}

# Download spanner-refdb plugin
ARG SPANNER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-spanner-refdb-bazel-master-master/lastSuccessfulBuild/artifact/bazel-bin/plugins/spanner-refdb/spanner-refdb.jar
RUN curl -f -k -o /var/plugins/spanner-refdb.jar ${SPANNER_REFDB_URL}

# Download pull-replication plugin
ARG PULL_REPLICATION_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-pull-replication-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/pull-replication/pull-replication.jar
RUN curl -f -k -o /var/plugins/pull-replication.jar ${PULL_REPLICATION_URL}

# Download events-broker lib
ARG EVENTS_BROKER_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/module-events-broker-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/events-broker/events-broker.jar
RUN curl -f -k -o /var/plugins/events-broker.jar ${EVENTS_BROKER_URL}

# Download events-kafka plugin
ARG EVENTS_KAFKA_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-events-kafka-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/events-kafka/events-kafka.jar
RUN curl -f -k -o /var/plugins/events-kafka.jar ${EVENTS_KAFKA_URL}

# Download websession-broker plugin
ARG WEBSESSION_BROKER_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-websession-broker-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/websession-broker/websession-broker.jar
RUN curl -f -k -o /var/plugins/websession-broker.jar ${WEBSESSION_BROKER_URL}

# Download multi-site plugin
ARG MULTI_SITE_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.11/job/plugin-multi-site-bazel-stable-3.11/lastSuccessfulBuild/artifact/bazel-bin/plugins/multi-site/multi-site.jar
RUN curl -f -k -o /var/plugins/multi-site.jar ${MULTI_SITE_URL}

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit && \
    chown -R gerrit:users /var/plugins && \
    chown -R gerrit:users /var/war
USER gerrit

RUN java -jar /var/gerrit/bin/gerrit.war init \
      --batch \
      --no-auto-start \
      -d /var/gerrit

ENTRYPOINT ["ash", "/var/tools/start"]
