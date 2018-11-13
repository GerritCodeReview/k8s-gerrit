FROM base:latest

# Install apache2
RUN apt-get update && apt-get -y install apache2 apache2-utils
RUN a2enmod cgi alias env ssl

# Configure git-http-backend
COPY git-https-backend.conf /etc/apache2/sites-available/
RUN ln -s \
  /etc/apache2/sites-available/git-https-backend.conf \
  /etc/apache2/sites-enabled/git-https-backend.conf
COPY git-http-backend.conf /etc/apache2/sites-available/
RUN ln -s \
  /etc/apache2/sites-available/git-http-backend.conf \
  /etc/apache2/sites-enabled/git-http-backend.conf
RUN sed -i -e 's/APACHE_RUN_USER=www-data/APACHE_RUN_USER=gerrit/' /etc/apache2/envvars
RUN sed -i -e 's/APACHE_RUN_GROUP=www-data/APACHE_RUN_GROUP=users/' /etc/apache2/envvars

COPY tools/* /var/tools/
COPY tools/create_repo.sh /var/cgi/create_repo.sh
RUN rm -f /etc/apache2/sites-enabled/000-default.conf

# Allow incoming traffic
EXPOSE 80
EXPOSE 443

ARG GERRIT_UID=1000
RUN useradd gerrit -u $GERRIT_UID -g users

VOLUME ["/var/gerrit/git", "/var/apache/credentials", "/var/log/apache2"]

# Start
ENTRYPOINT ["/bin/bash", "-c", "/var/tools/verify_fs_permissions && /var/tools/start"]
