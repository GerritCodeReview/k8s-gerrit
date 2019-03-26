FROM base:latest

RUN apt-get update && \
    apt-get -y install \
      curl \
      unzip \
      openjdk-8-jdk && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/war && \
    mkdir -p /var/plugins

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/view/Gerrit/job/Gerrit-bazel-stable-2.16/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

# Install Healthcheck plugin
ARG HEALTHCHECK_JAR_URL=https://gerrit-ci.gerritforge.com/view/Plugins-stable-2.16/job/plugin-healthcheck-bazel-stable-2.16/lastSuccessfulBuild/artifact/bazel-genfiles/plugins/healthcheck/healthcheck.jar
RUN curl -k -o /var/plugins/healthcheck.jar ${HEALTHCHECK_JAR_URL} && ln -s /var/plugins /var/gerrit/plugins

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit && \
    chown -R gerrit:users /var/war
USER gerrit

RUN java -jar /var/gerrit/bin/gerrit.war init \
      --batch \
      --no-auto-start \
      -d /var/gerrit

ENTRYPOINT ["/bin/bash", "/var/tools/start"]
