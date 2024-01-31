ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache \
      coreutils \
      curl \
      openssh-keygen \
      openjdk17

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/gerrit/plugins && \
    mkdir -p /var/plugins && \
    mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.9.1.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.9/job/plugin-healthcheck-bazel-stable-3.9/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.9/job/module-global-refdb-bazel-stable-3.9/lastSuccessfulBuild/artifact/bazel-bin/plugins/global-refdb/global-refdb.jar
RUN curl -k -o /var/plugins/global-refdb.jar ${GLOBAL_REFDB_URL}

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-master/job/plugin-high-availability-bazel-master/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
RUN curl -k -o /var/plugins/high-availability.jar ${HA_JAR_URL}

# Download zookeeper-refdb plugin
ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.9/job/plugin-zookeeper-refdb-bazel-stable-3.9/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
RUN curl -k -o /var/plugins/zookeeper-refdb.jar ${ZOOKEEPER_REFDB_URL}

# Download spanner-refdb plugin
ARG SPANNER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.9/job/plugin-spanner-refdb-bazel-master-stable-3.9/lastSuccessfulBuild/artifact/bazel-bin/plugins/spanner-refdb/spanner-refdb.jar
RUN curl -k -o /var/plugins/spanner-refdb.jar ${SPANNER_REFDB_URL}

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
