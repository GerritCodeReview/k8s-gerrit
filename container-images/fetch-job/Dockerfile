ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache yq

COPY tools/* /var/tools/

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/fetch-job.sh"]
