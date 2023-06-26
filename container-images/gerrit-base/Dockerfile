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
    mkdir -p /var/plugins && \
    mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.8.0.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && \
    ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Download healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.8/job/plugin-healthcheck-bazel-master-stable-3.8/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar
RUN curl -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && \
    ln -s /var/plugins/healthcheck.jar /var/gerrit/plugins/healthcheck.jar

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
