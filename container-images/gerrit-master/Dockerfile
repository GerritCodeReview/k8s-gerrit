FROM gerrit-base:latest

RUN java -jar /var/gerrit/bin/gerrit.war init --batch --no-auto-start -d /var/gerrit

COPY tools/* /var/tools/
