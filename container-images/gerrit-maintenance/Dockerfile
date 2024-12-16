ARG TAG=latest
FROM base:${TAG}

RUN apk update && \
    apk add --no-cache \
      python3

RUN git clone -b master --depth 1 https://gerrit.googlesource.com/gerrit && \
    cd gerrit && \
    git checkout origin/master && \
    cp -R contrib/maintenance /var/tools && \
    cd .. && \
    rm -rf gerrit

RUN mkdir -p /var/gerrit/etc && \
    chown -R gerrit /var/gerrit && \
    git config -f /var/gerrit/etc/gerrit.config gerrit.basePath /var/gerrit/git

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/gerrit-maintenance.py"]
