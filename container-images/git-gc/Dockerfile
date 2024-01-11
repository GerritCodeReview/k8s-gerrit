ARG TAG=latest
FROM base:${TAG}

COPY tools/* /var/tools/

RUN mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git && \
    ln -sf /var/tools/git-gc-preserve /usr/bin/git-gc-preserve

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/gc.sh"]
