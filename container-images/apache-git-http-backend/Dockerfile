FROM base:latest

# Install apache2
RUN apt-get update && \
    apt-get -y install \
      apache2 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    a2enmod \
      cgi \
      alias \
      env \
      ssl && \
    rm -f /etc/apache2/sites-enabled/000-default.conf

# Configure git-http-backend
COPY git-https-backend.conf /etc/apache2/sites-available/
COPY git-http-backend.conf /etc/apache2/sites-available/
RUN ln -s \
      /etc/apache2/sites-available/git-https-backend.conf \
      /etc/apache2/sites-enabled/git-https-backend.conf && \
    ln -s \
      /etc/apache2/sites-available/git-http-backend.conf \
      /etc/apache2/sites-enabled/git-http-backend.conf
RUN sed -i -e 's/APACHE_RUN_USER=www-data/APACHE_RUN_USER=gerrit/' /etc/apache2/envvars && \
    sed -i -e 's/APACHE_RUN_GROUP=www-data/APACHE_RUN_GROUP=users/' /etc/apache2/envvars

COPY tools/start /var/tools/start
COPY tools/create_repo.sh /var/cgi/create_repo.sh

RUN mkdir -p /var/gerrit/git && \
    chown -R gerrit:users /var/gerrit/git

# Start
ENTRYPOINT ["/var/tools/start"]
