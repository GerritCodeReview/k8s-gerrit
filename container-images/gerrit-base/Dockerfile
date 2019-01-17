FROM base:latest

RUN apt-get update && \
    apt-get -y install \
      curl \
      openjdk-8-jdk && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /var/gerrit/bin && \
    mkdir -p /var/gerrit/etc && \
    mkdir -p /var/war

# Download Gerrit release
ARG GERRIT_WAR_URL=https://gerrit-releases.storage.googleapis.com/gerrit-3.0.1.war
RUN curl -k -o /var/war/gerrit.war ${GERRIT_WAR_URL} && ln -s /var/war/gerrit.war /var/gerrit/bin/gerrit.war

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
