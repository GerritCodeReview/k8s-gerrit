# Configuring the MySQL-database

To install a MySQL database with the gerrit-master chart, set `mysql.enabled`to
true in the `values.yaml`. This will then install the
[mysql chart](https://github.com/helm/charts/tree/master/stable/mysql)
onto the Kubernetes cluster as a dependency.

## Create certificates for SSL-encrypted communication

For SSL-encrypted communication, a set of certificates is needed. Use the
following commands to create them after adjusting the subject strings:

```sh
openssl genrsa -out ./ca.key.pem 4096

openssl req \
    -key ./ca.key.pem \
    -new \
    -x509 \
    -days 7300 \
    -sha256 \
    -out ./ca.cert.pem \
    -subj "/C=DE/O=Gerrit/CN=gerrit-db-master" \
    -nodes

openssl genrsa -out ./master.key.pem 4096

openssl req \
    -key ./master.key.pem \
    -new \
    -sha256 \
    -out ./master.csr.pem \
    -subj "/C=DE/O=Gerrit/CN=gerrit-db-master" \
    -nodes

openssl x509 \
    -req \
    -CA ./ca.cert.pem \
    -CAkey ./ca.key.pem \
    -CAcreateserial \
    -in ./master.csr.pem \
    -out ./master.cert.pem
```

## Configuration

### mysql-chart

The configuration of the database is done in the `values.yaml`of the gerrit-master
chart under the `mysql`-key. The complete list of options for the mysql-chart can
be viewed in the chart's [documentation](https://github.com/helm/charts/blob/master/stable/mysql/README.md).
The options referenced in the gerrit-master chart's `values.yaml` are listed here:

| Parameter                                  | Description                                                                                                                                          | Default                                                                           |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `mysql.enabled`                            | Whether to install the MySQL database                                                                                                                | `true`                                                                            |
| `mysql.image`                              | Which container image containing MySQL to use                                                                                                        | `mysql`                                                                           |
| `mysql.imageTag`                           | Tag of container image (usually the database version)                                                                                                | `5.5.61`                                                                          |
| `mysql.mysqlRootPassword`                  | Password of the database `root` user                                                                                                                 | `big_secret`                                                                      |
| `mysql.mysqlUser`                          | Database user (The technical user used by Gerrit)                                                                                                    | `gerrit`                                                                          |
| `mysql.mysqlPassword`                      | Password of the database user                                                                                                                        | `secret`                                                                          |
| `mysql.livenessProbe.initialDelaySeconds`  | Delay before liveness probe is initiated                                                                                                             | `30`                                                                              |
| `mysql.livenessProbe.periodSeconds`        | How often to perform the probe                                                                                                                       | `10`                                                                              |
| `mysql.livenessProbe.timeoutSeconds`       | When the probe times out                                                                                                                             | `5`                                                                               |
| `mysql.livenessProbe.successThreshold`     | Minimum consecutive successes for the probe to be considered successful after having failed.                                                         | `1`                                                                               |
| `mysql.livenessProbe.failureThreshold`     | Minimum consecutive failures for the probe to be considered failed after having succeeded.                                                           | `3`                                                                               |
| `mysql.readinessProbe.initialDelaySeconds` | Delay before readiness probe is initiated                                                                                                            | `5`                                                                               |
| `mysql.readinessProbe.periodSeconds`       | How often to perform the probe                                                                                                                       | `10`                                                                              |
| `mysql.readinessProbe.timeoutSeconds`      | When the probe times out                                                                                                                             | `1`                                                                               |
| `mysql.readinessProbe.successThreshold`    | Minimum consecutive successes for the probe to be considered successful after having failed.                                                         | `1`                                                                               |
| `mysql.readinessProbe.failureThreshold`    | Minimum consecutive failures for the probe to be considered failed after having succeeded.                                                           | `3`                                                                               |
| `mysql.persistence.enabled`                | Create a volume to store data                                                                                                                        | `true`                                                                            |
| `mysql.persistence.size`                   | Size of persistent volume claim                                                                                                                      | `8Gi`                                                                             |
| `mysql.persistence.storageClass`           | Type of persistent volume claim                                                                                                                      | `default`                                                                         |
| `mysql.persistence.accessMode`             | ReadWriteOnce or ReadOnly                                                                                                                            | `ReadWriteOnce`                                                                   |
| `mysql.resources`                          | Configure the amount of resources the pod requests/is allowed                                                                                        | `requests.cpu: 250m`                                                              |
|                                            |                                                                                                                                                      | `requests.memory: 1Gi`                                                            |
|                                            |                                                                                                                                                      | `limits.cpu: 250m`                                                                |
|                                            |                                                                                                                                                      | `limits.memory: 1Gi`                                                              |
| `mysql.configurationFiles`                 | Add configuration files for MySQL                                                                                                                    | `mysql.cnf` (check the [mysql.cnf-section](#mysql.cnf) for configuration options) |
| `mysql.initializationFiles`                | Add scripts that are executed, when the database is started the first time                                                                           | `initialize_reviewdb.sql` (Should not be changed)                                 |
| `mysql.service.type`                       | Type of the Service used to expose the database                                                                                                      | `NodePort`                                                                        |
| `mysql.service.port`                       | The port used to expose the database                                                                                                                 | `3306`                                                                            |
| `ssl.enabled`                              | Setup and use SSL for MySQL connections                                                                                                              | `false`                                                                           |
| `ssl.secret`                               | Name of the secret containing the SSL certificates                                                                                                   | master-ssl-certs                                                                  |
| `ssl.certificates[0].name`                 | Name of the secret containing the SSL certificates                                                                                                   | master-ssl-certs                                                                  |
| `ssl.certificates[0].ca`                   | CA certificate (if using replication use the CA created [peviously](#Create-certificates-for-SSL-encrypted-communication))                           | `-----BEGIN CERTIFICATE-----`                                                     |
| `ssl.certificates[0].cert`                 | Server certificate (public key) (if using replication use the certificate created [peviously](#Create-certificates-for-SSL-encrypted-communication)) | `-----BEGIN CERTIFICATE-----`                                                     |
| `ssl.certificates[0].key`                  | Server key (private key) (if using replication use the key created [peviously](#Create-certificates-for-SSL-encrypted-communication))                | `-----BEGIN RSA PRIVATE KEY-----`                                                 |

### mysql.cnf

The configuration file for the MySQL-server is provided under the key
`mysql.configurationsFiles.mysql.cnf`. The provided values provide necessary
configuration to receive replicated databases from the master database.

Some options should be adapted to the respective setup:

| Parameter       | Description                                              | Default     |
|-----------------|----------------------------------------------------------|-------------|
| `log_bin`       | Name of transaction logs (used for database replication) | `mysql-bin` |
| `binlog_format` | Format of the binlogs (Has to be the same as on master)  | `row`       |
| `server-id`     | ID unique in the MySQL setup                             | `42`        |

In addition, if using SSL for MySQL-requests the following options have to be made
available by uncommenting them. The values must not be changed, when using the chart:

```sh
ssl-ca=/ssl/ca.pem
ssl-cert=/ssl/server-cert.pem
ssl-key=/ssl/server-key.pem
```
