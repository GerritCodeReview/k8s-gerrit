# A ReviewDB for Gerrit on Kubernetes

Gerrit requires a database to store user and change data. The database is commonly
called ReviewDB. Gerrit provides a set of options, which database technology to
use. This chart is meant to install a database and initialize it, so it can be
used as a ReviewDB for Gerrit-master and -slave setups installed with the
respective charts. Currently, the following databases sre supported by this chart:

- MySQL

***note
The gerrit-master and gerrit-slave charts also support H2, but since H2-databases
do not require a separate server, this chart is not required in that case.
***

***note
With upcoming versions of Gerrit, the ReviewDB will be completely migrated to
NoteDB, removing the dependency on a external relational database.
***

## Installation

To install a MySQL database with the reviewdb chart, set `mysql.enabled`to
true in the `values.yaml`. This will then install the
[mysql chart](https://github.com/helm/charts/tree/master/stable/mysql)
onto the Kubernetes cluster as a dependency.

To install the chart with the release name `reviewdb`, execute:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install ./reviewdb \
  --dep-up \
  -n reviewdb \
  -f <path-to-custom-values>.yaml
```

## Configuration

### Common

#### Container images

| Parameter                                  | Description                                          | Default                                                              |
|--------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------|
| `images.registry.name`                     | The image registry to pull the container images from | ``                                                                   |
| `images.registry.ImagePullSecret.name`     | Name of the ImagePullSecret                          | `image-pull-secret` (if empty no image pull secret will be deployed) |
| `images.registry.ImagePullSecret.create`   | Whether to create an ImagePullSecret                 | `false`                                                              |
| `images.registry.ImagePullSecret.username` | The image registry username                          | `nil`                                                                |
| `images.registry.ImagePullSecret.password` | The image registry password                          | `nil`                                                                |
| `images.version`                           | The image version (image tag) to use                 | `latest`                                                             |
| `images.imagePullPolicy`                   | Image pull policy                                    | `Always`                                                             |

#### Storage classes

For information of how a `StorageClass` is configured in Kubernetes, read the
[official Documentation](https://kubernetes.io/docs/concepts/storage/storage-classes/#introduction).

| Parameter                              | Description                                                       | Default                                           |
|----------------------------------------|-------------------------------------------------------------------|---------------------------------------------------|
| `storageClasses.default.name`          | The name of the default StorageClass (RWO)                        | `default`                                         |
| `storageClasses.default.create`        | Whether to create the StorageClass                                | `false`                                           |
| `storageClasses.default.provisioner`   | Provisioner of the StorageClass                                   | `kubernetes.io/aws-ebs`                           |
| `storageClasses.default.reclaimPolicy` | Whether to `Retain` or `Delete` volumes, when they become unbound | `Delete`                                          |
| `storageClasses.default.parameters`    | Parameters for the provisioner                                    | `parameters.type: gp2`, `parameters.fsType: ext4` |

#### Replication

***note
Currently, only master-slave replication is possible. Thus, enabling replication
always means to use the database as slave.
***

| Parameter | Description                                            | Default |
|-----------|--------------------------------------------------------|---------|
| `isSlave` | Whether to switch on replication from another database | `false` |


#### MySQL

***note
Not all options available in the mysql-chart that is used as a dependency are
listed in the `values.yaml`-file of this chart. The complete list of options for
the mysql-chart can be viewed in the chart's
[documentation](https://github.com/helm/charts/blob/master/stable/mysql/README.md).
***

| Parameter                                  | Description                                                                                                                                     | Default                                                                                        |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `mysql.enabled`                            | Whether to install the MySQL database                                                                                                           | `true`                                                                                         |
| `mysql.replication`                        | Only used, if `isSlave` is `true`                                                                                                               | `{}`                                                                                           |
| `mysql.replication.config.masterHost`      | Hostname of the Mysql database master                                                                                                           | `mysql.example.com`                                                                            |
| `mysql.replication.config.masterPort`      | Port of the Mysql database master                                                                                                               | `3306`                                                                                         |
| `mysql.replication.config.masterUser`      | Username of technical user created for replication                                                                                              | `repl`                                                                                         |
| `mysql.replication.config.masterPassword`  | Password of technical user created for replicataion                                                                                             | `password`                                                                                     |
| `mysql.replication.config.masterLogFile`   | Transaction log file at timepoint of dump as retrieved [here](#Create-database-dump-and-note-database-state)                                    | `mysql-bin.000001`                                                                             |
| `mysql.replication.config.masterLogPos`    | Transaction log position at timepoint of dump as retrieved [here](#Create-database-dump-and-note-database-state)                                | `111`                                                                                          |
| `mysql.replication.dbDumpAcceptPath`       | Path, where the replication init script will expect the database dump file to appear                                                            | `/var/data/db/master_dump.sql`                                                                 |
| `mysql.image`                              | Which container image containing MySQL to use                                                                                                   | `mysql`                                                                                        |
| `mysql.imageTag`                           | Tag of container image (usually the database version)                                                                                           | `5.5.61`                                                                                       |
| `mysql.mysqlRootPassword`                  | Password of the database `root` user                                                                                                            | `big_secret`                                                                                   |
| `mysql.mysqlUser`                          | Database user (The technical user used by Gerrit)                                                                                               | `gerrit`                                                                                       |
| `mysql.mysqlPassword`                      | Password of the database user                                                                                                                   | `secret`                                                                                       |
| `mysql.livenessProbe.initialDelaySeconds`  | Delay before liveness probe is initiated                                                                                                        | `30`                                                                                           |
| `mysql.livenessProbe.periodSeconds`        | How often to perform the probe                                                                                                                  | `10`                                                                                           |
| `mysql.livenessProbe.timeoutSeconds`       | When the probe times out                                                                                                                        | `5`                                                                                            |
| `mysql.livenessProbe.successThreshold`     | Minimum consecutive successes for the probe to be considered successful after having failed.                                                    | `1`                                                                                            |
| `mysql.livenessProbe.failureThreshold`     | Minimum consecutive failures for the probe to be considered failed after having succeeded.                                                      | `3`                                                                                            |
| `mysql.readinessProbe.initialDelaySeconds` | Delay before readiness probe is initiated                                                                                                       | `5`                                                                                            |
| `mysql.readinessProbe.periodSeconds`       | How often to perform the probe                                                                                                                  | `10`                                                                                           |
| `mysql.readinessProbe.timeoutSeconds`      | When the probe times out                                                                                                                        | `1`                                                                                            |
| `mysql.readinessProbe.successThreshold`    | Minimum consecutive successes for the probe to be considered successful after having failed.                                                    | `1`                                                                                            |
| `mysql.readinessProbe.failureThreshold`    | Minimum consecutive failures for the probe to be considered failed after having succeeded.                                                      | `3`                                                                                            |
| `mysql.persistence.enabled`                | Create a volume to store data                                                                                                                   | `true`                                                                                         |
| `mysql.persistence.size`                   | Size of persistent volume claim                                                                                                                 | `8Gi`                                                                                          |
| `mysql.persistence.storageClass`           | Type of persistent volume claim                                                                                                                 | `default`                                                                                      |
| `mysql.persistence.accessMode`             | ReadWriteOnce or ReadOnly                                                                                                                       | `ReadWriteOnce`                                                                                |
| `mysql.resources`                          | Configure the amount of resources the pod requests/is allowed                                                                                   | `requests.cpu: 250m`                                                                           |
|                                            |                                                                                                                                                 | `requests.memory: 1Gi`                                                                         |
|                                            |                                                                                                                                                 | `limits.cpu: 250m`                                                                             |
|                                            |                                                                                                                                                 | `limits.memory: 1Gi`                                                                           |
| `mysql.configurationFiles`                 | Add configuration files for MySQL                                                                                                               | `{}` (check the [Configuration files-section](#Configuration-files) for configuration options) |
| `mysql.initializationFiles`                | Add scripts that are executed, when the database is started the first time                                                                      | `{}` (check the [Initialization files-section](#Initialization-files) for details)             |
| `mysql.service.type`                       | Type of the Service used to expose the database                                                                                                 | `NodePort`                                                                                     |
| `mysql.service.port`                       | The port used to expose the database                                                                                                            | `3306`                                                                                         |
| `mysql.ssl.enabled`                        | Setup and use SSL for MySQL connections                                                                                                         | `false`                                                                                        |
| `mysql.ssl.secret`                         | Name of the secret containing the SSL certificates (Has to be different between multiple instances running in the same cluster)                 | `ssl-certs`                                                                                    |
| `mysql.ssl.certificates[0].name`           | Name of the secret containing the SSL certificates (Has to be different between multiple instances running in the same cluster)                 | `ssl-certs`                                                                                    |
| `mysql.ssl.certificates[0].ca`             | CA certificate (if using replication use the CA created [here](#create-certificates-for-ssl_encrypted-communication))                           | `-----BEGIN CERTIFICATE-----`                                                                  |
| `mysql.ssl.certificates[0].cert`           | Server certificate (public key) (if using replication use the certificate created [here](#create-certificates-for-ssl_encrypted-communication)) | `-----BEGIN CERTIFICATE-----`                                                                  |
| `mysql.ssl.certificates[0].key`            | Server key (private key) (if using replication use the key created [here](#create-certificates-for-ssl_encrypted-communication))                | `-----BEGIN RSA PRIVATE KEY-----`                                                              |

##### Configuration files

The configuration file for the MySQL-server is provided under the keys
`mysql.configurationsFiles.*.cnf`. There are three different config-files provided:

- `common.cnf`: Contains configuration, which is the same for master- and slave-
databases:

| Parameter       | Description                                              | Default           |
|-----------------|----------------------------------------------------------|-------------------|
| `log-bin`       | Name of transaction logs (used for database replication) | `mysql-bin`       |
| `log-bin-index` | Name of transaction log indices                          | `mysql-bin.index` |
| `log-error`     | Error log file                                           | `error.log`       |
| `binlog_format` | Format of the binlogs (Has to be the same as on master)  | `row`             |

In addition, if using SSL for MySQL-requests the following options have to be made
available by uncommenting them. The values must not be changed, when using the chart:

```sh
ssl-ca=/ssl/ca.pem
ssl-cert=/ssl/server-cert.pem
ssl-key=/ssl/server-key.pem
```

- `mysql-master.cnf`: Contains configuration specific for the master database:

| Parameter   | Description                  | Default |
|-------------|------------------------------|---------|
| `server-id` | ID unique in the MySQL setup | `1`     |

- `mysql-slave.cnf`: Contains configuration specific for the slave database:

| Parameter             | Description                                                                                                                 | Default                                                                   |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `relay-log`           | The slave's relay log location                                                                                              | `/var/lib/mysql/relay.log`                                                |
| `relay-log-info-file` | The slave's relay log info file location                                                                                    | `/var/lib/mysql/relay-log.info`                                           |
| `relay-log-index`     | The slave's relay log index location                                                                                        | `/var/lib/mysql/relay-log.index`                                          |
| `log_slave_updates`   | Whether to log slave update                                                                                                 | `1`                                                                       |
| `sql_mode`            | Configure SQL-mode                                                                                                          | `"ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION"` |
| `read_only`           | Toggle read only mode. In production this should be on (`1`). The test mode of the Gerrit slave expects it to be off (`0`). | `0`                                                                       |
| `replicate-ignore-db` | Databases not to replicate (replicating the `mysql`-DB for example would overwrite database users)                          | `mysql`                                                                   |
| `server-id`           | ID unique in the MySQL setup                                                                                                | `42`                                                                      |

The `mysql-master.cnf`- and `mysql-slave.cnf`-files are mutually exclusive.
Comment out the contents of the file, that is not needed, depending on installing
a master or slave database.

##### Initialization files

- `initialize_reviewdb.sql`

Creates a database called 'reviewdb', that can be used by Gerrit for the ReviewDB.
Leave this file unchanged.

- `create_repl_user.sql`

Creates a user, that can be used for database replication. This user is only needed
on the master database and only, when the data is supposed to be replicated to
slaves. To use it, uncomment the code and change the username, password and
certificate subject as needed.

## Aditional configuration steps

### Create certificates for SSL-encrypted communication

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

openssl genrsa -out ./slave.key.pem 4096

openssl req \
    -key ./slave.key.pem \
    -new \
    -sha256 \
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

***note
The `CN` has to be the hostname of the database instances. In case the database
is running on Kubernetes it can be the service name.
***

### Configuring a master DB instance for replication

For the replication to work, the MySQL database master has to be configured
accordingly and some data about the database state has to be collected. The
necessary steps are detailed in this section. If it is not planned to replicate
the master database, skip this section.

#### Create database dump and note database state

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

The filename and position have to be entered in the database slave's `values.yaml`
under `mysql.replication.config.masterLogFile` and
`mysql.replication.config.masterLogPos`.

Dump the content of the database:

```sh
mysqldump --user=root -p --databases reviewdb > ./master_dump.sql
```

Afterwards, the master can be started again:

```sql
START MASTER;
```

## Initialize replication

### MySQL

Deploying the reviewdb chart with replication enabled, will create a MySQL
database with a technical user to be used by the Gerrit slave and an empty
ReviewDB database. In addition a Job will be deployed that waits for a database
dump to be copied into the container to the location specified in
`mysql.replication.dbDumpAcceptPath`. The dump file can be copied into the
container using kubectl:

```sh
JOB_POD=$(kubectl get pod -l app=mysql-replication-init -o jsonpath="{.items[0].metadata.name}")
kubectl cp <PATH_TO_DUMP> ${JOB_POD}:<DB_DUMP_ACCEPT_PATH>
```

As soon as the file is fully copied into the container, the script will load
the dump into the database and initialize the replication in the slave. The
database is then fully configured.
