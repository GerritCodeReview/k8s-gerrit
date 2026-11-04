ARG TAG=latest
FROM base:${TAG}

ARG GERRIT_VER=1f7db0e10692d5c94bad160884c92540a3164d18

RUN apk update && \
    apk add --no-cache \
      python3 \
      curl && \
    mkdir -p /var/tools && \
    curl https://gerrit.googlesource.com/site-maintenance/+archive/${GERRIT_VER}.tar.gz | \
        tar -xzf - -C /var/tools && \
    apk del --purge curl

RUN mkdir -p /var/gerrit/etc && \
    chown -R gerrit /var/gerrit && \
    git config -f /var/gerrit/etc/gerrit.config gerrit.basePath /var/gerrit/git

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/gerrit-maintenance.py"]
