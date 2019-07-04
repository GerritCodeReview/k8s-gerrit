FROM base:latest

# Install apache2
RUN apk update && \
    apk add --no-cache \
      apache2 \
      apache2-ctl \
      apache2-utils \
      apache2-ssl \
      git-daemon

# Configure git-http-backend
COPY config/git-https-backend.conf /etc/apache2/conf.d/
COPY config/git-http-backend.conf /etc/apache2/conf.d/
COPY config/envvars /usr/sbin/envvars
COPY config/httpd.conf /etc/apache2/httpd.conf

COPY tools/start /var/tools/start
COPY tools/create_repo.sh /var/cgi/create_repo.sh

RUN mkdir -p /var/gerrit/git && \
    mkdir -p /var/log/apache2 && \
    chown -R gerrit:users /var/gerrit/git && \
    chown -R gerrit:users /var/log/apache2

# Start
ENTRYPOINT ["ash", "/var/tools/start"]
