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

# TODO: Install websession-flatfile plugin with the mechanism implemented
# in change 218572 as soon as the plugin is fixed.
COPY websession-flatfile.jar /var/plugins/websession-flatfile.jar

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit && \
    chown -R gerrit:users /var/war && \
    chown -R gerrit:users /var/plugins
USER gerrit

RUN java -jar /var/gerrit/bin/gerrit.war init \
      --batch \
      --no-auto-start \
      -d /var/gerrit

ENTRYPOINT ["/bin/bash", "/var/tools/start"]
