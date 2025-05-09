ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache \
      coreutils \
      openssh-keygen \
      openjdk21

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/gerrit/plugins && \
    mkdir -p /var/plugins && \
    mkdir -p /var/war

ARG GERRIT_BRANCH=master

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war
ADD ${GERRIT_WAR_URL} /var/war/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/job/plugin-healthcheck-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
ADD ${HEALTHCHECK_JAR_URL} /var/plugins/healthcheck.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://gerrit-ci.gerritforge.com/job/module-global-refdb-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/global-refdb/global-refdb.jar
ADD ${GLOBAL_REFDB_URL} /var/plugins/global-refdb.jar

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/job/plugin-high-availability-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
ADD ${HA_JAR_URL} /var/plugins/high-availability.jar

# Download zookeeper-refdb plugin
ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/job/plugin-zookeeper-refdb-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
ADD ${ZOOKEEPER_REFDB_URL} /var/plugins/zookeeper-refdb.jar

# Download spanner-refdb plugin
ARG SPANNER_REFDB_URL=https://gerrit-ci.gerritforge.com/job/plugin-spanner-refdb-bazel-master-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/spanner-refdb/spanner-refdb.jar
ADD ${SPANNER_REFDB_URL} /var/plugins/spanner-refdb.jar

# Download pull-replication plugin
ARG PULL_REPLICATION_URL=https://gerrit-ci.gerritforge.com/job/plugin-pull-replication-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/pull-replication/pull-replication.jar
ADD ${PULL_REPLICATION_URL} /var/plugins/pull-replication.jar

# Download events-broker lib
ARG EVENTS_BROKER_URL=https://gerrit-ci.gerritforge.com/job/module-events-broker-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/events-broker/events-broker.jar
ADD ${EVENTS_BROKER_URL} /var/plugins/events-broker.jar

# Download events-kafka plugin
ARG EVENTS_KAFKA_URL=https://gerrit-ci.gerritforge.com/job/plugin-events-kafka-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/events-kafka/events-kafka.jar
ADD ${EVENTS_KAFKA_URL} /var/plugins/events-kafka.jar

# Download websession-broker plugin
ARG WEBSESSION_BROKER_URL=https://gerrit-ci.gerritforge.com/job/plugin-websession-broker-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/websession-broker/websession-broker.jar
ADD ${WEBSESSION_BROKER_URL} /var/plugins/websession-broker.jar

# Download multi-site plugin
ARG MULTI_SITE_URL=https://gerrit-ci.gerritforge.com/job/plugin-multi-site-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/multi-site/multi-site.jar
ADD ${MULTI_SITE_URL} /var/plugins/multi-site.jar

# Download index-elasticsearch lib
ARG INDEX_ELASTICSEARCH_URL=https://gerrit-ci.gerritforge.com/job/module-index-elasticsearch-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/index-elasticsearch/index-elasticsearch.jar
ADD ${INDEX_ELASTICSEARCH_URL} /var/plugins/index-elasticsearch.jar

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit && \
    chown -R gerrit:users /var/plugins && \
    chown -R gerrit:users /var/war
USER gerrit

RUN java -jar /var/war/gerrit.war init \
      --batch \
      --no-auto-start \
      -d /var/gerrit

ENTRYPOINT ["ash", "/var/tools/start"]
