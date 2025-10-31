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

ARG GERRIT_BRANCH=master

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war
RUN curl -f -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/job/plugin-healthcheck-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -f -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://gerrit-ci.gerritforge.com/job/module-global-refdb-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/global-refdb/global-refdb.jar
RUN curl -f -k -o /var/plugins/global-refdb.jar ${GLOBAL_REFDB_URL}

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/job/plugin-high-availability-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
RUN curl -f -k -o /var/plugins/high-availability.jar ${HA_JAR_URL}

# Download spanner-refdb plugin
ARG SPANNER_REFDB_URL=https://gerrit-ci.gerritforge.com/job/plugin-spanner-refdb-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/spanner-refdb/spanner-refdb.jar
RUN curl -f -k -o /var/plugins/spanner-refdb.jar ${SPANNER_REFDB_URL}

# Download events-broker lib
ARG EVENTS_BROKER_URL=https://gerrit-ci.gerritforge.com/job/module-events-broker-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/events-broker/events-broker.jar
RUN curl -f -k -o /var/plugins/events-broker.jar ${EVENTS_BROKER_URL}

# Download index-elasticsearch lib
ARG INDEX_ELASTICSEARCH_URL=https://gerrit-ci.gerritforge.com/job/module-index-elasticsearch-bazel-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/bazel-bin/plugins/index-elasticsearch/index-elasticsearch.jar
RUN curl -f -k -o /var/plugins/index-elasticsearch.jar ${INDEX_ELASTICSEARCH_URL}

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
