ARG TAG=latest
FROM base:${TAG}

COPY tools/* /var/tools/

RUN mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git

USER gerrit

VOLUME ["/var/gerrit/shared/git"]

ENTRYPOINT ["/var/tools/gc.sh"]
