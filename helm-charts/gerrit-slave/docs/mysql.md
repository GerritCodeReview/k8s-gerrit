# Configuring the MySQL-slave

To install a MySQL slave database with the gerrit-slave chart, set
`database.provider` to `mysql` and `mysql.enabled`to true in the `values.yaml`.
This will then install the [mysql chart](https://github.com/helm/charts/tree/master/stable/mysql)
onto the Kubernetes cluster as a dependency of the gerrit-slave chart.

## Configuring the master DB instance

For the replication to work, the MySQL database master has to be configured
accordingly and some data about the database state has to be collected. The
necessary steps are detailed in this section. If it is not planned to replicate
the master database, skip this section.

### Create technical user

Connect to the MySQL database master and create a technical user to handle the
replication:

```sql
CREATE USER 'repl' IDENTIFIED BY 'password';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'
  IDENTIFIED BY 'password'
  REQUIRE SUBJECT '/C=DE/O=Gerrit/CN=gerrit-db-slave';
FLUSH PRIVILEGES;
```

The username, password and certificate subject can be chosen as needed, but should
be written down, since they are needed in coming steps.

### Create certificates for SSL-encrypted communication

For SSL-encrypted communication, a set of certificates is needed. If the master
does not yet possess a CA, private- and public key, use the following commands
to create them after adjusting the subject strings:

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

Then a private and a public key for the slave has to be created. If the master
did already possess a CA, change the corresponding paths in the commands below.
The subject string has to be the same as the one used, when creating the
[MySQL user for replication](#Create-technical-user). The content of the
CA-certificate and the slave's private and public key (here: `slave.key.pem` and
`slave.cert.pem`) have to be noted for later use.

```sh
openssl genrsa -out ./slave.key.pem 4096

openssl req \
    -key ./slave.key.pem \
    -new -sha256 \
    -out ./slave.csr.pem \
    -subj "/C=DE/O=Gerrit/CN=gerrit-db-slave" \
    -nodes

openssl x509 \
    -req \
    -CA ./ca.cert.pem \
    -CAkey ./ca.key.pem \
    -CAcreateserial \
    -in ./slave.csr.pem \
    -out ./slave.cert.pem
```

### Configure the master database

The master DB has to be configured for replication by adding the following entries
to the configuration-file of the MySQL instance:

```python
[mysqld]
server-id=1                     # Has to be unique under all masters/slaves.
log_bin=mysql-bin               # Name of the logs used for replication

ssl-ca=/ssl/ca.pem              # Location of the CA-certificate
ssl-cert=/ssl/server-cert.pem   # Location of the public key
ssl-key=/ssl/server-key.pem     # Location of the private key
```

### Create database dump and note database state

In the next steps the content of the database has to be retrieved and the corresponding
status of the transaction logs has to be retrieved. Depending on the traffic the
database receives, the master DB should be stopped for these steps, since the
information could get out off sync, if the data is changed inbetween the steps:

```sql
STOP MASTER;
```

Retrieve the status of the master:

```sql
SHOW MASTER STATUS;

  +------------------+----------+--------------+------------------+-------------------+
  | File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
  +------------------+----------+--------------+------------------+-------------------+
  | mysql-bin.000004 | 69444891 |              |                  |                   |
  +------------------+----------+--------------+------------------+-------------------+
```

The filename and position should be written down, since they will be needed for
the configuration of the slave.

Dump the content of the database:

```sh
mysqldump --user=root -p --all-databases > ./master_dump.sql
```

Afterwards, the master can be started again:

```sql
START MASTER;
```

## Configuration

### mysql-chart

The configuration of the database is done in the `values.yaml`of the gerrit-slave
chart under the `mysql`-key. The complete list of options for the mysql-chart can
be viewed in the chart's [documentation](https://github.com/helm/charts/blob/master/stable/mysql/README.md).
The options referenced in the gerrit-slave chart's `values.yaml` are listed here:

| Parameter                                  | Description                                                                                                                                          | Default                                                                           |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `mysql.enabled`                            | Whether to install the MySQL database                                                                                                                | `true`                                                                            |
| `mysql.image`                              | Which container image containing MySQL to use                                                                                                        | `mysql`                                                                           |
| `mysql.imageTag`                           | Tag of container image (usually the database version)                                                                                                | `5.5.61`                                                                          |
| `mysql.mysqlRootPassword`                  | Password of the database `root` user                                                                                                                 | `big_secret`                                                                      |
| `mysql.mysqlUser`                          | Database user (The technical user used by the Gerrit slave)                                                                                          | `gerrit`                                                                          |
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
| `ssl.secret`                               | Name of the secret containing the SSL certificates                                                                                                   | slave-ssl-certs                                                                   |
| `ssl.certificates[0].name`                 | Name of the secret containing the SSL certificates                                                                                                   | slave-ssl-certs                                                                   |
| `ssl.certificates[0].ca`                   | CA certificate (if using replication use the CA created [peviously](#Create-certificates-for-SSL-encrypted-communication))                           | `-----BEGIN CERTIFICATE-----`                                                     |
| `ssl.certificates[0].cert`                 | Server certificate (public key) (if using replication use the certificate created [peviously](#Create-certificates-for-SSL-encrypted-communication)) | `-----BEGIN CERTIFICATE-----`                                                     |
| `ssl.certificates[0].key`                  | Server key (private key) (if using replication use the key created [peviously](#Create-certificates-for-SSL-encrypted-communication))                | `-----BEGIN RSA PRIVATE KEY-----`                                                 |

### mysql.cnf

The configuration file for the MySQL-server is provided under the key
`mysql.configurationsFiles.mysql.cnf`. The provided values provide necessary
configuration to receive replicated databases from the master database. The
following options should normally not be changed:

```sh
[mysqld]

log-bin=/var/lib/mysql/bin.log
log-bin-index=/var/lib/mysql/log-bin.index
log-error=/var/lib/mysql/error.log

relay-log=/var/lib/mysql/relay.log
relay-log-info-file=/var/lib/mysql/relay-log.info
relay-log-index=/var/lib/mysql/relay-log.index

log-error=/var/lib/mysql/error.log
log_slave_updates = 1

sql_mode="ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION"
```

The other provided options should be adapted to the respective setup:

| Parameter             | Description                                                                                                                 | Default |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------|---------|
| `read_only`           | Toggle read only mode. In production this should be on (`1`). The test mode of the Gerrit slave expects it to be off (`0`). | `0`     |
| `replicate-ignore-db` | Databases not to replicate (replicating the `mysql`-DB for example would overwrite database users)                          | `mysql` |
| `binlog_format`       | Format of the binlogs (Has to be the same as on master)                                                                     | `row`   |
| `server-id`           | ID unique in the MySQL setup                                                                                                | `42`    |

In addition, if using SSL for MySQL-requests the following options have to be made
available by uncommenting them. The values must not be changed, when using the chart:

```sh
ssl-ca=/ssl/ca.pem
ssl-cert=/ssl/server-cert.pem
ssl-key=/ssl/server-key.pem
```

### Replication

The replication of the MySQL database from master to slave is performed using the
replication functionality provided by MySQL. To start replication a database dump
from the master has to be loaded into the database slave. Then the slave has to
be configured for replication and replication has to be started. This is done by
a job provided by the chart.

The Job needs to be configured with the data retrieved from the database master
by configuring the corresponding values in the `values.yaml`-file:

| Parameter                                          | Description                                                                                                            | Default                        |
|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| `database.replication.mysql.config.masterHost`     | Hostname of the Mysql database master                                                                                  | `mysql.example.com`            |
| `database.replication.mysql.config.masterPort`     | Port of the Mysql database master                                                                                      | `3306`                         |
| `database.replication.mysql.config.masterUser`     | Username of technical user created [previously](#Create-technical-user)                                                | `repl`                         |
| `database.replication.mysql.config.masterPassword` | Password of technical user created [previously](#Create-technical-user)                                                | `password`                     |
| `database.replication.mysql.config.masterLogFile`  | Transaction log file at timepoint of dump as retrieved [previously](#Create-database-dump-and-note-database-state)     | `mysql-bin.000001`             |
| `database.replication.mysql.config.masterLogPos`   | Transaction log position at timepoint of dump as retrieved [previously](#Create-database-dump-and-note-database-state) | `111`                          |
| `database.replication.mysql.dbDumpAcceptPath`      | Path, where the replication init script will expect the database dump file to appear                                   | `/var/data/db/master_dump.sql` |

## Initialize replication

Deploying the gerrit-slave chart with the configuration detailed above, will
create a MySQL database with a technical user to be used by the Gerrit
slave and an empty ReviewDB database. In addition a Job will be deployed that
waits for a database dump to be copied into the container to the location specified
in `database.replication.mysql.dbDumpAcceptPath`. The dump file can be copied
using kubectl:

```sh
JOB_POD=$(kubectl get pod -l app=mysql-replication-init -o jsonpath="{.items[0].metadata.name}")
kubectl cp <PATH_TO_DUMP> ${JOB_POD}:<DB_DUMP_ACCEPT_PATH>
```

As soon as the file is fully copied into the container, the script will load
the dump into the database and initialize the replication in the slave. The
database is then fully configured.