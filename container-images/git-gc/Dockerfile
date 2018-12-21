FROM base:latest

COPY tools/* /var/tools/

RUN mkdir -p /var/log/git && \
    chown gerrit:users /var/log/git

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/gc-all.sh"]
