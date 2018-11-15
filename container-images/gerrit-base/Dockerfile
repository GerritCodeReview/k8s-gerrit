FROM base:latest

RUN apt-get update && apt-get -y install curl unzip openjdk-8-jdk

RUN mkdir -p /var/gerrit/bin
RUN mkdir -p /var/gerrit/etc

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-ci.gerritforge.com/view/Gerrit/job/Gerrit-bazel-stable-2.16/lastSuccessfulBuild/artifact/gerrit/bazel-bin/gerrit.war
RUN curl -k -o /var/gerrit/bin/gerrit.war ${GERRIT_WAR_URL}

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users

# Allow incoming traffic
EXPOSE 29418 8080

RUN chown -R gerrit:users /var/gerrit
USER gerrit

ENTRYPOINT ["/bin/bash", "/var/tools/start"]
