# Gerrit slave on Kubernetes

Gerrit is a web-based code review tool, which acts as a Git server. On large setups
Gerrit servers can see a sizable amount of traffic from git operations performed by
developers and build servers. The major part of requests are read-only requests
(e.g. by `git fetch` operations). To take some load of the Gerrit master server,
Gerrit slaves can be deployed to serve read-only requests.

This helm chart provides a Gerrit slave setup that can be deployed on Kubernetes.
The Gerrit slave is capable of receiving replicated git repositories from a
Gerrit master. The slave can deploy its own database, that replicates the data
from the Gerrit master's database (Currently only MySQL databases are supported).
The Gerrit slave can then serve authenticated read-only requests.

## Prerequisites

- Helm and Tiller (of course)

    (Check out [this guide](https://docs.helm.sh/using_helm/#quickstart-guide)
    how to install and use helm.)

- Access to a provisioner for persistent volumes with `Read Write Many (RWM)`-
  capability.

    A list of applicaple volume types can be found
    [here](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes).
    This project was developed using the
    [NFS-server-provisioner helm chart](https://github.com/helm/charts/tree/master/stable/nfs-server-provisioner),
    a NFS-provisioner deployed in the Kubernetes cluster itself. Refer to
    [this guide](/helm-charts/gerrit-slave/docs/nfs-provisioner.md) of how to
    deploy it in context of this project.

- A domain name that is configured to point to the IP address of the node running
  the Ingress controller on the kubernetes cluster (as described
  [here](http://alesnosek.com/blog/2017/02/14/accessing-kubernetes-pods-from-outside-of-the-cluster/)).

- (Optional: Required, if SSL is configured)
  A [Java keystore](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#httpd.sslKeyStore)
  to be used by Gerrit.

## Installing the Chart

***note
**ATTENTION:** The value for `gerritSlave.ingress.host` is required for rendering
the chart's templates. The nature of the value does not allow defaults.
Thus a custom `values.yaml`-file setting this value is required!
***

To install the chart with the release name `gerrit-slave`, execute:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install ./gerrit-slave \
  --dep-up \
  -n gerrit-slave \
  -f <path-to-custom-values>.yaml
```

The command deploys the Gerrit slave on the current Kubernetes cluster. The
[configuration section](#Configuration) lists the parameters that can be
configured during installation.

## Configuration

The following sections list the configurable values in `values.yaml`. To configure
a Gerrit slave setup, make a copy of the `values.yaml`-file and change the
parameters as needed. The configuration can be applied by installing the chart as
described [above](#Installing-the-chart).

In addition, single options can be set without creating a custom `values.yaml`:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install ./gerrit-slave \
  --dep-up \
  -n gerrit-slave \
  --set=gitRepositoryStorage.size=100Gi,gitBackend.replicas=2
```

### Container images

| Parameter                                  | Description                                          | Default                                                              |
|--------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------|
| `images.registry.name`                     | The image registry to pull the container images from | ``                                                                   |
| `images.registry.ImagePullSecret.name`     | Name of the ImagePullSecret                          | `image-pull-secret` (if empty no image pull secret will be deployed) |
| `images.registry.ImagePullSecret.create`   | Whether to create an ImagePullSecret                 | `false`                                                              |
| `images.registry.ImagePullSecret.username` | The image registry username                          | `nil`                                                                |
| `images.registry.ImagePullSecret.password` | The image registry password                          | `nil`                                                                |
| `images.version`                           | The image version (image tag) to use                 | `latest`                                                             |
| `images.imagePullPolicy`                   | Image pull policy                                    | `Always`                                                             |

### Storage classes

For information of how a `StorageClass` is configured in Kubernetes, read the
[official Documentation](https://kubernetes.io/docs/concepts/storage/storage-classes/#introduction).

| Parameter                              | Description                                                       | Default                                           |
|----------------------------------------|-------------------------------------------------------------------|---------------------------------------------------|
| `storageClasses.default.name`          | The name of the default StorageClass (RWO)                        | `default`                                         |
| `storageClasses.default.create`        | Whether to create the StorageClass                                | `false`                                           |
| `storageClasses.default.provisioner`   | Provisioner of the StorageClass                                   | `kubernetes.io/aws-ebs`                           |
| `storageClasses.default.reclaimPolicy` | Whether to `Retain` or `Delete` volumes, when they become unbound | `Delete`                                          |
| `storageClasses.default.parameters`    | Parameters for the provisioner                                    | `parameters.type: gp2`, `parameters.fsType: ext4` |
| `storageClasses.shared.name`           | The name of the shared StorageClass (RWM)                         | `shared-storage`                                  |
| `storageClasses.shared.create`         | Whether to create the StorageClass                                | `false`                                           |
| `storageClasses.shared.provisioner`    | Provisioner of the StorageClass                                   | `nfs`                                             |
| `storageClasses.shared.reclaimPolicy`  | Whether to `Retain` or `Delete` volumes, when they become unbound | `Delete`                                          |
| `storageClasses.shared.parameters`     | Parameters for the provisioner                                    | `parameters.mountOptions: vers=4.1`               |

### Storage for Git repositories

| Parameter                   | Description                                     | Default |
|-----------------------------|-------------------------------------------------|---------|
| `gitRepositoryStorage.size` | Size of the volume storing the Git repositories | `5Gi`   |

### Apache-Git-HTTP-Backend (Git-Backend)

| Parameter                                | Description                                                                 | Default                                                                   |
|------------------------------------------|-----------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `gitBackend.image`                       | Image name of the Apache-git-http-backend container image                   | `k8s-gerrit/apache-git-http-backend`                                      |
| `gitBackend.replicas`                    | Number of pod replicas to deploy                                            | `1`                                                                       |
| `gitBackend.resources`                   | Configure the amount of resources the pod requests/is allowed               | `requests.cpu: 100m`                                                      |
|                                          |                                                                             | `requests.memory: 256Mi`                                                  |
|                                          |                                                                             | `limits.cpu: 100m`                                                        |
|                                          |                                                                             | `limits.memory: 256Mi`                                                    |
| `gitBackend.credentials.htpasswd`        | `.htpasswd`-file containing username/password-credentials for accessing git | `git:$apr1$O/LbLKC7$Q60GWE7OcqSEMSfe/K8xU.` (user: git, password: secret) |
| `gitBackend.logging.persistence.enabled` | Whether to persist logs                                                     | `true`                                                                    |
| `gitBackend.logging.persistence.size`    | Storage size for persisted logs                                             | `1Gi`                                                                     |
| `gitBackend.service.type`                | Which kind of Service to deploy                                             | `LoadBalancer`                                                            |
| `gitBackend.service.http.enabled`        | Whether to serve HTTP-requests (needed for Ingress)                         | `true`                                                                    |
| `gitBackend.service.http.port`           | Port over which to expose HTTP                                              | `80`                                                                      |
| `gitBackend.service.https.enabled`       | Whether to serve HTTPS-requests                                             | `false`                                                                   |
| `gitBackend.service.https.port`          | Port over which to expose HTTPS                                             | `443`                                                                     |
| `gitBackend.service.https.cert`          | Public SSL server certificate                                               | `-----BEGIN CERTIFICATE-----`                                             |
| `gitBackend.service.https.key`           | Private SSL server certificate                                              | `-----BEGIN RSA PRIVATE KEY-----`                                         |
| `gitBackend.ingress.enabled`             | Whether to deploy an Ingress                                                | `false`                                                                   |
| `gitBackend.ingress.host`                | Host name to use for the Ingress (required for Ingress)                     | `nil`                                                                     |
| `gitBackend.ingress.alias`               | Optional: ALias host name for the Ingress                                   | `nil`                                                                     |
| `gitBackend.ingress.tls.enabled`         | Whether to enable TLS termination in the Ingress                            | `false`                                                                   |
| `gitBackend.ingress.tls.cert`            | Public SSL server certificate                                               | `-----BEGIN CERTIFICATE-----`                                             |
| `gitBackend.ingress.tls.key`             | Private SSL server certificate                                              | `-----BEGIN RSA PRIVATE KEY-----`                                         |

***note
At least one endpoint (HTTP and/or HTTPS) has to be enabled in the service!
***

### Git garbage collection

| Parameter                           | Description                                                      | Default                  |
|-------------------------------------|------------------------------------------------------------------|--------------------------|
| `gitGC.image`                       | Image name of the Git-GC container image                         | `k8s-gerrit/git-gc`      |
| `gitGC.schedule`                    | Cron-formatted schedule with which to run Git garbage collection | `0 6,18 * * *`           |
| `gitGC.resources`                   | Configure the amount of resources the pod requests/is allowed    | `requests.cpu: 100m`     |
|                                     |                                                                  | `requests.memory: 256Mi` |
|                                     |                                                                  | `limits.cpu: 100m`       |
|                                     |                                                                  | `limits.memory: 256Mi`   |
| `gitGC.logging.persistence.enabled` | Whether to persist logs                                          | `true`                   |
| `gitGC.logging.persistence.size`    | Storage size for persisted logs                                  | `1Gi`                    |

### Database

The Gerrit slave requires a database containing the user data associated with the
replicated Git repositories, which is implemented as a database slave containing
the replicated data of the master database.

***note
Future implementations will provide the possibility to bring custom databases
from different providers, but so far the setup expects to setup its own MySQL
database.
***

| Parameter                      | Description                                            | Default |
|--------------------------------|--------------------------------------------------------|---------|
| `database.provider`            | Database type/provider to be used (Available: mysql)   | `mysql` |
| `database.replication.enabled` | Whether to initialize replication from master database | `true`  |

The usual way to provide a database is meant to deploy it as a dependency of
this chart. Since the configuration of the database is different depending on
the database provider used, the configuration is described in separate documents:

- [MySQL](/helm-charts/gerrit-slave/docs/mysql.md)

### Gerrit slave

***note
The way the Jetty servlet used by Gerrit works, the Gerrit slave component of the
gerrit-slave chart actually requires the URL to be known, when the chart is installed.
The suggested way to do that is to use the provided Ingress resource. This requires
that a URL is available and that the DNS is configured to point the URL to the
IP of the node the Ingress controller is running on!
***

***note
Setting the canonical web URL in the gerrit.config to the host used for the Ingress
is mandatory, if access to the Gerrit slave is required!
***

| Parameter                                    | Description                                                                                                              | Default                           |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| `gerritMaster.images.gerritInit`             | Image name of the Gerrit init container image                                                                            | `k8s-gerrit/gerrit-init`          |
| `gerritMaster.images.gerritSlave`            | Image name of the Gerrit slave container image                                                                           | `k8s-gerrit/gerrit-slave`         |
| `gerritSlave.initializeTestSite.enabled`     | Enable the initialization of a site. USE ONLY for testing, if you do not plan to replicate repositories or the database. | `true`                            |
| `gerritSlave.resources`                      | Configure the amount of resources the pod requests/is allowed                                                            | `requests.cpu: 1`                 |
|                                              |                                                                                                                          | `requests.memory: 5Gi`            |
|                                              |                                                                                                                          | `limits.cpu: 1`                   |
|                                              |                                                                                                                          | `limits.memory: 6Gi`              |
| `gerritSlave.logging.persistence.enabled`    | Whether to persist logs                                                                                                  | `true`                            |
| `gerritSlave.logging.persistence.size`       | Storage size for persisted logs                                                                                          | `1Gi`                             |
| `gerritSlave.h2Database.persistence.enabled` | Whether to persist h2 databases                                                                                          | `true`                            |
| `gerritSlave.h2Database.persistence.size`    | Storage size for persisted h2 databases                                                                                  | `1Gi`                             |
| `gerritSlave.service.type`                   | Which kind of Service to deploy                                                                                          | `NodePort`                        |
| `gerritSlave.service.http.port`              | Port over which to expose HTTP                                                                                           | `80`                              |
| `gerritSlave.ingress.host`                   | REQUIRED: Host name to use for the Ingress (required for Ingress)                                                        | `nil`                             |
| `gerritSlave.ingress.alias`                  | Optional: Alias host name for the Ingress                                                                                | `nil`                             |
| `gerritSlave.ingress.tls.enabled`            | Whether to enable TLS termination in the Ingress                                                                         | `false`                           |
| `gerritSlave.ingress.tls.cert`               | Public SSL server certificate                                                                                            | `-----BEGIN CERTIFICATE-----`     |
| `gerritSlave.ingress.tls.key`                | Private SSL server certificate                                                                                           | `-----BEGIN RSA PRIVATE KEY-----` |
| `gerritSlave.keystore`                       | base64-encoded Java keystore (`cat keystore.jks | base64`) to be used by Gerrit, when using SSL                          | `nil`                             |
| `gerritSlave.config.gerrit`                  | The contents of the gerrit.config                                                                                        | [see here](#Gerrit-config-files)  |
| `gerritSlave.config.secure`                  | The contents of the secure.config                                                                                        | [see here](#Gerrit-config-files)  |

### Gerrit config files

The gerrit-slave chart provides a ConfigMap containing the `gerrit.config` and a
Secret containing the `secure.config` to configure the Gerrit installation in the
Gerrit slave component. The content of the `gerrit.config` and `secure.config`
can be set in the `values.yaml` under the keys `gerritSlave.config.gerrit` and
`gerritSlave.config.secure` respectively. All configuration options are described
in detail in the [official documentation of Gerrit](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html).
Some options however have to be set in a specified way for the Gerrit slave to
work as intended:

- `gerrit.basePath`

    Path to the directory containing the repositories. The chart mounts this
    directory from a persistent volume to `/var/gerrit/git` in the container. For
    Gerrit to find the correct directory, this has to be set to `git`.

- `gerrit.serverId`

    In Gerrit-version higher than 2.14 Gerrit needs a server ID, which is used by
    NoteDB. Gerrit would usually generate a random ID on startup, but since the
    gerrit.config file is read only, when mounted as a ConfigMap this fails.
    Thus the server ID has to be set manually!

- `gerrit.canonicalWebUrl`

    The canonical web URL has to be set to the Ingress host.

- `database.*`

    The default settings are configured to use the MySQL-database installed as a
    dependency and if the chart is installed with the release name set to
    `gerrit-slave`. Only change this, if you decide to use a different database or
    changed the default settings for the mysql-chart.

    With newer versions of the MySQL-driver used by Gerrit, using SSL-encrypted
    communication with the database is enforced by default. This can be deactivated
    by setting the `useSSL=false`-option. To do that in Gerrit, the database-URL
    has to be provided manually:
    `url = jdbc:mysql://<db-host>:<db-port>/<db-name>?nullNamePatternMatchesAll=true&useSSL=false`

- `httpd.listenURL`

    This has to be set to `proxy-http://*:8080/` or `proxy-https://*:8080`,
    depending of TLS is enabled in the Ingress or not, otherwise the Jetty
    servlet will run into an endless redirect loop.

- `container.user`

    The technical user in the Gerrit slave container is called `gerrit`. Thus, this
    value is required to be `gerrit`.

- `container.slave`

    Since this chart is meant to install a Gerrit slave, this naturally has to be
    `true`.

- `container.javaHome`

    This has to be set to `/usr/lib/jvm/java-8-openjdk-amd64`, since this is
    the path of the Java installation in the container.

- `container.javaOptions`

    The maximum heap size has to be set. And its value has to be lower than the
    memory resource limit set for the container (e.g. `-Xmx4g`). In your calculation
    allow memory for other components running in the container.

## Upgrading the Chart

To upgrade an existing installation of the gerrit-slave chart, e.g. to install
a newer chart version or to use an updated custom `values.yaml`-file, execute
the following command:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm upgrade <release-name> \
  -f <path-to-custom-values>.yaml \
  ./gerrit-slave
```

## Uninstalling the Chart

To delete the chart from the cluster, use:

```sh
helm delete <release-name> \
  --purge
```
