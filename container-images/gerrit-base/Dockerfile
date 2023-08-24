ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache \
      coreutils \
      curl \
      openssh-keygen \
      openjdk11

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/gerrit/plugins && \
    mkdir -p /var/plugins/ha && \
    mkdir -p /var/plugins/zookeeper && \
    mkdir -p /var/libs/ha && \
    mkdir -p /var/war

# Download Gerrit release
# TODO: Revert back to use release versions as soon as change 383334 has been released
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/view/Gerrit/job/Gerrit-bazel-stable-3.8/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-healthcheck-bazel-master-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/module-global-refdb-bazel-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/global-refdb/global-refdb.jar
RUN curl -k -o /var/libs/ha/global-refdb.jar ${GLOBAL_REFDB_URL}

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-high-availability-bazel-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
RUN curl -k -o /var/plugins/ha/high-availability.jar ${HA_JAR_URL} && \
    cp /var/plugins/ha/high-availability.jar var/libs/ha/high-availability.jar

# Download zookeeper-refdb plugin
#ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-zookeeper-refdb-bazel-master-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.7/job/plugin-zookeeper-refdb-bazel-stable-3.7/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
RUN curl -k -o /var/plugins/zookeeper/zookeeper-refdb.jar ${ZOOKEEPER_REFDB_URL}

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
