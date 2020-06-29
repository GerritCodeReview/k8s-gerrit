# Gerrit replica on Kubernetes

Gerrit is a web-based code review tool, which acts as a Git server. On large setups
Gerrit servers can see a sizable amount of traffic from git operations performed by
developers and build servers. The major part of requests are read-only requests
(e.g. by `git fetch` operations). To take some load of the Gerrit server,
Gerrit replicas can be deployed to serve read-only requests.

This helm chart provides a Gerrit replica setup that can be deployed on Kubernetes.
The Gerrit replica is capable of receiving replicated git repositories from a
Gerrit. The Gerrit replica can then serve authenticated read-only requests.

***note
Gerrit versions before 3.0 are no longer supported, since the support of ReviewDB
was removed.
***

## Prerequisites

- Helm (>= version 3.0)

    (Check out [this guide](https://docs.helm.sh/using_helm/#quickstart-guide)
    how to install and use helm.)

- Access to a provisioner for persistent volumes with `Read Write Many (RWM)`-
  capability.

    A list of applicaple volume types can be found
    [here](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes).
    This project was developed using the
    [NFS-server-provisioner helm chart](https://github.com/helm/charts/tree/master/stable/nfs-server-provisioner),
    a NFS-provisioner deployed in the Kubernetes cluster itself. Refer to
    [this guide](/helm-charts/gerrit-replica/docs/nfs-provisioner.md) of how to
    deploy it in context of this project.

- A domain name that is configured to point to the IP address of the node running
  the Ingress controller on the kubernetes cluster (as described
  [here](http://alesnosek.com/blog/2017/02/14/accessing-kubernetes-pods-from-outside-of-the-cluster/)).

- (Optional: Required, if SSL is configured)
  A [Java keystore](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#httpd.sslKeyStore)
  to be used by Gerrit.

## Installing the Chart

***note
**ATTENTION:** The value for `gerritReplica.ingress.host` is required for rendering
the chart's templates. The nature of the value does not allow defaults.
Thus a custom `values.yaml`-file setting this value is required!
***

To install the chart with the release name `gerrit-replica`, execute:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install \
  gerrit-replica \  # release name
  ./gerrit-replica \  # path to chart
  -f <path-to-custom-values>.yaml
```

The command deploys the Gerrit replica on the current Kubernetes cluster. The
[configuration section](#Configuration) lists the parameters that can be
configured during installation.

The Gerrit replica requires the replicated `All-Projects.git`- and `All-Users.git`-
repositories to be present in the `/var/gerrit/git`-directory. The `gerrit-init`-
InitContainer will wait for this being the case. A way to do this is to access
the Gerrit replica pod and to clone the repositories from the primary Gerrit (Make
sure that you have the correct access rights do so.):

```sh
kubectl exec -it <gerrit-replica-pod> -c gerrit-init ash
gerrit@<gerrit-replica-pod>:/var/tools$ cd /var/gerrit/git
gerrit@<gerrit-replica-pod>:/var/gerrit/git$ git clone "http://gerrit.com/All-Projects" --mirror
Cloning into bare repository 'All-Projects.git'...
gerrit@<gerrit-replica-pod>:/var/gerrit/git$ git clone "http://gerrit.com/All-Users" --mirror
Cloning into bare repository 'All-Users.git'...
```

## Configuration

The following sections list the configurable values in `values.yaml`. To configure
a Gerrit replica setup, make a copy of the `values.yaml`-file and change the
parameters as needed. The configuration can be applied by installing the chart as
described [above](#Installing-the-chart).

In addition, single options can be set without creating a custom `values.yaml`:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install \
  gerrit-replica \  # release name
  ./gerrit-replica \  # path to chart
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

| Parameter                                  | Description                                                                        | Default                                                                   |
|--------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `gitBackend.image`                         | Image name of the Apache-git-http-backend container image                          | `k8s-gerrit/apache-git-http-backend`                                      |
| `gitBackend.replicas`                      | Number of pod replicas to deploy                                                   | `1`                                                                       |
| `gitBackend.maxSurge`                      | Max. percentage or number of pods allowed to be scheduled above the desired number | `25%`                                                                     |
| `gitBackend.maxUnavailable`                | Max. percentage or number of pods allowed to be unavailable at a time              | `100%`                                                                    |
| `gitBackend.resources`                     | Configure the amount of resources the pod requests/is allowed                      | `requests.cpu: 100m`                                                      |
|                                            |                                                                                    | `requests.memory: 256Mi`                                                  |
|                                            |                                                                                    | `limits.cpu: 100m`                                                        |
|                                            |                                                                                    | `limits.memory: 256Mi`                                                    |
| `gitBackend.credentials.htpasswd`          | `.htpasswd`-file containing username/password-credentials for accessing git        | `git:$apr1$O/LbLKC7$Q60GWE7OcqSEMSfe/K8xU.` (user: git, password: secret) |
| `gitBackend.logging.persistence.enabled`   | Whether to persist logs                                                            | `true`                                                                    |
| `gitBackend.logging.persistence.size`      | Storage size for persisted logs                                                    | `1Gi`                                                                     |
| `gitBackend.service.type`                  | Which kind of Service to deploy                                                    | `LoadBalancer`                                                            |
| `gitBackend.service.http.enabled`          | Whether to serve HTTP-requests (needed for Ingress)                                | `true`                                                                    |
| `gitBackend.service.http.port`             | Port over which to expose HTTP                                                     | `80`                                                                      |
| `gitBackend.service.https.enabled`         | Whether to serve HTTPS-requests                                                    | `false`                                                                   |
| `gitBackend.service.https.port`            | Port over which to expose HTTPS                                                    | `443`                                                                     |
| `gitBackend.service.https.cert`            | Public SSL server certificate                                                      | `-----BEGIN CERTIFICATE-----`                                             |
| `gitBackend.service.https.key`             | Private SSL server certificate                                                     | `-----BEGIN RSA PRIVATE KEY-----`                                         |
| `gitBackend.ingress.enabled`               | Whether to deploy an Ingress                                                       | `false`                                                                   |
| `gitBackend.ingress.host`                  | Host name to use for the Ingress (required for Ingress)                            | `nil`                                                                     |
| `gitBackend.ingress.maxBodySize`           | Maximum request body size allowed (Set to 0 for an unlimited request body size)    | `50m`                                                                     |
| `gitBackend.ingress.additionalAnnotations` | Additional annotations for the Ingress                                             | `nil`                                                                     |
| `gitBackend.ingress.tls.enabled`           | Whether to enable TLS termination in the Ingress                                   | `false`                                                                   |
| `gitBackend.ingress.tls.cert`              | Public SSL server certificate                                                      | `-----BEGIN CERTIFICATE-----`                                             |
| `gitBackend.ingress.tls.key`               | Private SSL server certificate                                                     | `-----BEGIN RSA PRIVATE KEY-----`                                         |

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

### Gerrit replica

***note
The way the Jetty servlet used by Gerrit works, the Gerrit replica component of the
gerrit-replica chart actually requires the URL to be known, when the chart is installed.
The suggested way to do that is to use the provided Ingress resource. This requires
that a URL is available and that the DNS is configured to point the URL to the
IP of the node the Ingress controller is running on!
***

***note
Setting the canonical web URL in the gerrit.config to the host used for the Ingress
is mandatory, if access to the Gerrit replica is required!
***

| Parameter                                     | Description                                                                                              | Default                           |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------|-----------------------------------|
| `gerritReplica.images.gerritInit`             | Image name of the Gerrit init container image                                                            | `k8s-gerrit/gerrit-init`          |
| `gerritReplica.images.gerritReplica`          | Image name of the Gerrit replica container image                                                         | `k8s-gerrit/gerrit-replica`       |
| `gerritReplica.replicas`                      | Number of pod replicas to deploy                                                                         | `1`                               |
| `gerritReplica.maxSurge`                      | Max. percentage or number of pods allowed to be scheduled above the desired number                       | `25%`                             |
| `gerritReplica.maxUnavailable`                | Max. percentage or number of pods allowed to be unavailable at a time                                    | `100%`                            |
| `gerritReplica.initializeTestSite.enabled`    | Enable the initialization of a site. USE ONLY for testing, if you do not plan to replicate repositories. | `true`                            |
| `gerritReplica.resources`                     | Configure the amount of resources the pod requests/is allowed                                            | `requests.cpu: 1`                 |
|                                               |                                                                                                          | `requests.memory: 5Gi`            |
|                                               |                                                                                                          | `limits.cpu: 1`                   |
|                                               |                                                                                                          | `limits.memory: 6Gi`              |
| `gerritReplica.persistence.enabled`           | Whether to persist the Gerrit site                                                                       | `true`                            |
| `gerritReplica.persistence.size`              | Storage size for persisted Gerrit site                                                                   | `10Gi`                            |
| `gerritReplica.service.type`                  | Which kind of Service to deploy                                                                          | `NodePort`                        |
| `gerritReplica.service.http.port`             | Port over which to expose HTTP                                                                           | `80`                              |
| `gerritReplica.ingress.host`                  | REQUIRED: Host name to use for the Ingress (required for Ingress)                                        | `nil`                             |
| `gerritReplica.ingress.additionalAnnotations` | Additional annotations for the Ingress                                                                   | `nil`                             |
| `gerritReplica.ingress.tls.enabled`           | Whether to enable TLS termination in the Ingress                                                         | `false`                           |
| `gerritReplica.ingress.tls.cert`              | Public SSL server certificate                                                                            | `-----BEGIN CERTIFICATE-----`     |
| `gerritReplica.ingress.tls.key`               | Private SSL server certificate                                                                           | `-----BEGIN RSA PRIVATE KEY-----` |
| `gerritReplica.keystore`                      | base64-encoded Java keystore (`cat keystore.jks | base64`) to be used by Gerrit, when using SSL          | `nil`                             |
| `gerritReplica.config.gerrit`                 | The contents of the gerrit.config                                                                        | [see here](#Gerrit-config-files)  |
| `gerritReplica.config.secure`                 | The contents of the secure.config                                                                        | [see here](#Gerrit-config-files)  |

### Gerrit config files

The gerrit-replica chart provides a ConfigMap containing the `gerrit.config` and a
Secret containing the `secure.config` to configure the Gerrit installation in the
Gerrit replica component. The content of the `gerrit.config` and `secure.config`
can be set in the `values.yaml` under the keys `gerritReplica.config.gerrit` and
`gerritReplica.config.secure` respectively. All configuration options are described
in detail in the [official documentation of Gerrit](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html).
Some options however have to be set in a specified way for the Gerrit replica to
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

- `httpd.listenURL`

    This has to be set to `proxy-http://*:8080/` or `proxy-https://*:8080`,
    depending of TLS is enabled in the Ingress or not, otherwise the Jetty
    servlet will run into an endless redirect loop.

- `container.user`

    The technical user in the Gerrit replica container is called `gerrit`. Thus, this
    value is required to be `gerrit`.

- `container.replica`

    Since this chart is meant to install a Gerrit replica, this naturally has to be
    `true`.

- `container.javaHome`

    This has to be set to `/usr/lib/jvm/java-8-openjdk-amd64`, since this is
    the path of the Java installation in the container.

- `container.javaOptions`

    The maximum heap size has to be set. And its value has to be lower than the
    memory resource limit set for the container (e.g. `-Xmx4g`). In your calculation
    allow memory for other components running in the container.

## Upgrading the Chart

To upgrade an existing installation of the gerrit-replica chart, e.g. to install
a newer chart version or to use an updated custom `values.yaml`-file, execute
the following command:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm upgrade \
  <release-name> \
  ./gerrit-replica \ # path to chart
  -f <path-to-custom-values>.yaml \
```

## Uninstalling the Chart

To delete the chart from the cluster, use:

```sh
helm delete <release-name>
```
