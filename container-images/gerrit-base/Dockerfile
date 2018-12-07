FROM base:latest

RUN apt-get update && apt-get -y install curl unzip openjdk-8-jdk

RUN mkdir -p /var/gerrit/bin
RUN mkdir -p /var/gerrit/etc
RUN mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/view/Gerrit/job/Gerrit-bazel-stable-2.16/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit
RUN chown -R gerrit:users /var/war
USER gerrit

ENTRYPOINT ["/bin/bash", "/var/tools/start"]
