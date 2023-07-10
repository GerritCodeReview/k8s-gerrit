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
    mkdir -p /var/gerrit/lib && \
    mkdir -p /var/plugins && \
    mkdir -p /var/libs && \
    mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.8.0.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-healthcheck-bazel-master-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

# Download high-availability plugin
ARG HA_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-high-availability-bazel-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar
RUN curl -k -o /var/plugins/high-availability.jar ${HA_JAR_URL} && \
    ln -s /var/plugins/high-availability.jar /var/gerrit/plugins/high-availability.jar

# Download global-refdb lib
ARG GLOBAL_REFDB_URL=https://repo1.maven.org/maven2/com/gerritforge/global-refdb/3.8.0-rc5/global-refdb-3.8.0-rc5.jar
RUN curl -k -o /var/libs/global-refdb.jar ${GLOBAL_REFDB_URL} && \
    ln -s /var/libs/global-refdb.jar /var/gerrit/lib/global-refdb.jar

# Download zookeeper-refdb plugin
ARG ZOOKEEPER_REFDB_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-zookeeper-refdb-bazel-master-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper-refdb/zookeeper-refdb.jar
RUN curl -k -o /var/plugins/zookeeper-refdb.jar ${ZOOKEEPER_REFDB_URL} && \
    ln -s /var/plugins/zookeeper-refdb.jar /var/gerrit/plugins/zookeeper-refdb.jar

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
