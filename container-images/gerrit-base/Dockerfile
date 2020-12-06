FROM base:latest

RUN apk update && \
    apk add --no-cache \
      curl \
      openssh-keygen \
      openjdk11

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/gerrit/plugins && \
    mkdir -p /var/resources/plugins && \
    mkdir -p /var/resources/multisite/plugins && \
    mkdir -p /var/resources/multisite/lib && \
    mkdir -p /var/resources/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.3.1.war
ADD ${GERRIT_WAR_URL} /var/resources/war/gerrit.war
RUN ln -s /var/resources/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ADD https://gerrit-ci.gerritforge.com/job/plugin-healthcheck-bazel-stable-3.3/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar /var/resources/plugins/healthcheck.jar
RUN ln -s /var/resources/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download components required for multisite setup
ADD https://gerrit-ci.gerritforge.com/job/plugin-zookeeper-refdb-bazel-stable-3.3/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar /var/resources/multisite/plugins/zookeeper-refdb.jar
ADD https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.3/job/plugin-kafka-events-bazel-stable-3.3/lastSuccessfulBuild/artifact/bazel-bin/plugins/kafka-events/kafka-events.jar /var/resources/multisite/plugins/kafka-events.jar
ADD https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.3/job/plugin-pull-replication-bazel-stable-3.3/lastSuccessfulBuild/artifact/bazel-bin/plugins/pull-replication/pull-replication.jar /var/resources/multisite/plugins/pull-replication.jar
ADD https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.3/job/plugin-websession-broker-bazel-stable-3.3/lastSuccessfulBuild/artifact/bazel-bin/plugins/websession-broker/websession-broker.jar /var/resources/multisite/plugins/websession-broker.jar

ADD https://repo1.maven.org/maven2/com/gerritforge/global-refdb/3.3.0/global-refdb-3.3.0.jar /var/resources/multisite/lib/global-refdb.jar
ADD https://repo1.maven.org/maven2/com/gerritforge/events-broker/3.3.0/events-broker-3.3.0.jar /var/resources/multisite/lib/events-broker.jar

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit && \
    chown -R gerrit:users /var/resources
USER gerrit

RUN java -jar /var/gerrit/bin/gerrit.war init \
      --batch \
      --no-auto-start \
      -d /var/gerrit

ENTRYPOINT ["ash", "/var/tools/start"]
