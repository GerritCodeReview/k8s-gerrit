ARG TAG=latest
FROM base:${TAG}

# Install apache2
RUN apk update && \
    apk add --no-cache \
      apache2 \
      apache2-ctl \
      apache2-utils \
      git-daemon \
      logrotate && \
    rm /etc/apache2/conf.d/default.conf && \
    rm /etc/apache2/conf.d/info.conf

# Configure git-http-backend
COPY config/git-http-backend.conf /etc/apache2/conf.d/
COPY config/envvars /usr/sbin/envvars
COPY config/httpd.conf /etc/apache2/httpd.conf
COPY config/logrotation /etc/logrotate.d/apache2

COPY tools/start /var/tools/start
COPY tools/project_admin.sh /var/cgi/project_admin.sh

RUN mkdir -p /var/gerrit/git && \
    mkdir -p /var/log/apache2 && \
    chown -R gerrit:users /var/gerrit/git && \
    chown -R gerrit:users /var/log/apache2 && \
    chown -R gerrit:users /run/apache2

USER gerrit

# Start
ENTRYPOINT ["ash", "/var/tools/start"]
