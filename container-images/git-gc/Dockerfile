ARG TAG=latest
FROM base:${TAG}

COPY tools/* /var/tools/

ARG JGIT_VER="7.8.1.202611041320-r"
ADD https://repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit.pgm/${JGIT_VER}/org.eclipse.jgit.pgm-${JGIT_VER}.sh \
        /var/tools/jgit

RUN apk update && \
    apk add --no-cache \
        coreutils \
        openjdk25 && \
    chmod 0755 /var/tools/jgit && \
    ln -sf /var/tools/jgit /usr/bin/jgit

RUN ln -sf /var/tools/git-gc-preserve /usr/bin/git-gc-preserve

USER gerrit

VOLUME ["/var/gerrit/git"]

ENTRYPOINT ["/var/tools/gc.sh"]
