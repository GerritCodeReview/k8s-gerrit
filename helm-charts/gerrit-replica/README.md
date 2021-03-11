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
**ATTENTION:** The value for `ingress.host` is required for rendering
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

### CA certificate

Some application may require TLS verification. If the default CA built into the
containers is not enough a custom CA certificate can be given to the deployment.
Note, that Gerrit will require its CA in a JKS keytore, which is described below.

| Parameter | Description                                                                | Default |
|-----------|----------------------------------------------------------------------------|---------|
| `caCert`  | CA certificate for TLS verification (if not set, the default will be used) | `None`  |

### Workaround for NFS

Kubernetes will not be able to adapt the ownership of the files within NFS
volumes. Thus, a workaround exists that will add init-containers and jobs to
adapt file ownership. Also the ID-domain will be configured to ensure correct
ID-mapping.

| Parameter                | Description                                                                | Default           |
|--------------------------|----------------------------------------------------------------------------|-------------------|
| `nfsWorkaround.enabled`  | Whether the volume used is an NFS-volume                                   | `false`           |
| `nfsWorkaround.idDomain` | The ID-domain that should be used to map user-/group-IDs for the NFS mount | `localdomain.com` |


### Network policies

| Parameter                  | Description                                      | Default      |
|----------------------------|--------------------------------------------------|--------------|
| `networkPolicies.enabled`  | Whether to enable preconfigured NetworkPolicies  | `false`      |
| `networkPolicies.dnsPorts` | List of ports used by DNS-service (e.g. KubeDNS) | `[53, 8053]` |

The NetworkPolicies provided here are quite strict and do not account for all
possible scenarios. Thus, custom NetworkPolicies have to be added, e.g. for
connecting to a database. On the other hand some defaults may be not restrictive
enough. By default, the ingress traffic of the git-backend pod is not restricted.
Thus, every source (with the right credentials) could push to the git-backend.
To add an additional layer of security, the ingress rule could be defined more
finegrained. The chart provides the possibility to define custom rules for ingress-
traffic of the git-backend pod under `gitBackend.networkPolicy.ingress`.
Depending on the scenario, there are different ways to restrict the incoming
connections.

If the replicator (e.g. Gerrit) is running in a pod on the same cluster,
a podSelector (and namespaceSelector, if the pod is running in a different
namespace) can be used to whitelist the traffic:

```yaml
gitBackend:
  networkPolicy:
    ingress:
    - from:
      - podSelector:
          matchLabels:
            app: gerrit
```

If the replicator is outside the cluster, the IP of the replicator can also be
whitelisted, e.g.:

```yaml
gitBackend:
  networkPolicy:
    ingress:
    - from:
      - ipBlock:
          cidr: xxx.xxx.0.0/16
```

The same principle also applies to other use cases, e.g. connecting to a database.
For more information about the NetworkPolicy resource refer to the
[Kubernetes documentation](https://kubernetes.io/docs/concepts/services-networking/network-policies/).

### Storage for Git repositories

| Parameter                               | Description                                     | Default                |
|-----------------------------------------|-------------------------------------------------|------------------------|
| `gitRepositoryStorage.externalPVC.use`  | Whether to use a PVC deployed outside the chart | `false`                |
| `gitRepositoryStorage.externalPVC.name` | Name of the external PVC                        | `git-repositories-pvc` |
| `gitRepositoryStorage.size`             | Size of the volume storing the Git repositories | `5Gi`                  |

If the git repositories should be persisted even if the chart is deleted and in
a way that the volume containing them can be mounted by the reinstalled chart,
the PVC claiming the volume has to be created independently of the chart. To use
the external PVC, set `gitRepositoryStorage.externalPVC.enabled` to `true` and
give the name of the PVC under `gitRepositoryStorage.externalPVC.name`.

### Storage for Logs

In addition to collecting logs with a log collection tool like Promtail, the logs
can also be stored in a persistent volume. This volume has to be a read-write-many
volume to be able to be used by multiple pods.

| Parameter                          | Description                                        | Default                  |
|------------------------------------|----------------------------------------------------|--------------------------|
| `logStorage.enabled`               | Whether to enable persistence of logs              | `false`                  |
| `logStorage.externalPVC.use`       | Whether to use a PVC deployed outside the chart    | `false`                  |
| `logStorage.externalPVC.name`      | Name of the external PVC                           | `gerrit-logs-pvc`        |
| `logStorage.size`                  | Size of the volume                                 | `5Gi`                    |
| `logStorage.cleanup.enabled`       | Whether to regularly delete old logs               | `false`                  |
| `logStorage.cleanup.schedule`      | Cron schedule defining when to run the cleanup job | `0 0 * * *`              |
| `logStorage.cleanup.retentionDays` | Number of days to retain the logs                  | `14`                     |
| `logStorage.cleanup.resources`     | Resources the container is allowed to use          | `requests.cpu: 100m`     |
|                                    |                                                    | `requests.memory: 256Mi` |
|                                    |                                                    | `limits.cpu: 100m`       |
|                                    |                                                    | `limits.memory: 256Mi`   |

Each pod will create a separate folder for its logs, allowing to trace logs to
the respective pods.

### Istio

Istio can be used as an alternative to Kubernetes Ingresses to manage the traffic
into the cluster and also inside the cluster. This requires istio to be installed
beforehand. Some guidance on how to set up istio can be found [here](/Documentation/istio.md).
The helm chart expects `istio-injection` to be enabled in the namespace, in which
it will be installed.

In the case istio is used, all configuration for ingresses in the chart will be
ignored.

| Parameter                 | Description                                                               | Default                           |
|---------------------------|---------------------------------------------------------------------------|-----------------------------------|
| `istio.enabled`           | Whether istio should be used (requires istio to be installed)             | `false`                           |
| `istio.host`              | Hostname (CNAME must point to istio ingress gateway loadbalancer service) | `nil`                             |
| `istio.tls.enabled`       | Whether to enable TLS                                                     | `false`                           |
| `istio.tls.secret.create` | Whether to create TLS certificate secret                                  | `true`                            |
| `istio.tls.secret.name`   | Name of external secret containing TLS certificates                       | `nil`                             |
| `istio.tls.cert`          | TLS certificate                                                           | `-----BEGIN CERTIFICATE-----`     |
| `istio.tls.key`           | TLS key                                                                   | `-----BEGIN RSA PRIVATE KEY-----` |
| `istio.ssh.enabled`       | Whether to enable SSH                                                     | `false`                           |

### Ingress

As an alternative to istio the Nginx Ingress controller can be used to manage
ingress traffic.

| Parameter                       | Description                                                                     | Default                           |
|---------------------------------|---------------------------------------------------------------------------------|-----------------------------------|
| `ingress.enabled`               | Whether to deploy an Ingress                                                    | `false`                           |
| `ingress.host`                  | Host name to use for the Ingress (required for Ingress)                         | `nil`                             |
| `ingress.maxBodySize`           | Maximum request body size allowed (Set to 0 for an unlimited request body size) | `50m`                             |
| `ingress.additionalAnnotations` | Additional annotations for the Ingress                                          | `nil`                             |
| `ingress.tls.enabled`           | Whether to enable TLS termination in the Ingress                                | `false`                           |
| `ingress.tls.secret.create`     | Whether to create a TLS-secret                                                  | `true`                            |
| `ingress.tls.secret.name`       | Name of an external secret that will be used as a TLS-secret                    | `nil`                             |
| `ingress.tls.cert`              | Public SSL server certificate                                                   | `-----BEGIN CERTIFICATE-----`     |
| `ingress.tls.key`               | Private SSL server certificate                                                  | `-----BEGIN RSA PRIVATE KEY-----` |

### Promtail Sidecar

To collect Gerrit logs, a Promtail sidecar can be deployed into the Gerrit replica
pods. This can for example be used together with the [gerrit-monitoring](https://gerrit-review.googlesource.com/admin/repos/gerrit-monitoring)
project.

| Parameter                        | Description                                                         | Default                       |
|----------------------------------|---------------------------------------------------------------------|-------------------------------|
| `promtailSidecar.enabled`        | Whether to install the Promatil sidecar container                   | `false`                       |
| `promtailSidecar.image`          | The promtail container image to use                                 | `grafana/promtail`            |
| `promtailSidecar.version`        | The promtail container image version                                | `1.3.0`                       |
| `promtailSidecar.resources`      | Configure the amount of resources the container requests/is allowed | `requests.cpu: 100m`          |
|                                  |                                                                     | `requests.memory: 128Mi`      |
|                                  |                                                                     | `limits.cpu: 200m`            |
|                                  |                                                                     | `limits.memory: 128Mi`        |
| `promtailSidecar.tls.skipverify` | Whether to skip TLS verification                                    | `true`                        |
| `promtailSidecar.tls.caCert`     | CA certificate for TLS verification                                 | `-----BEGIN CERTIFICATE-----` |
| `promtailSidecar.loki.url`       | URL to reach Loki                                                   | `loki.example.com`            |
| `promtailSidecar.loki.user`      | Loki user                                                           | `admin`                       |
| `promtailSidecar.loki.password`  | Loki password                                                       | `secret`                      |


### Apache-Git-HTTP-Backend (Git-Backend)

| Parameter                                  | Description                                                                        | Default                                                                   |
|--------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `gitBackend.image`                         | Image name of the Apache-git-http-backend container image                          | `k8s-gerrit/apache-git-http-backend`                                      |
| `gitBackend.replicas`                      | Number of pod replicas to deploy                                                   | `1`                                                                       |
| `gitBackend.maxSurge`                      | Max. percentage or number of pods allowed to be scheduled above the desired number | `25%`                                                                     |
| `gitBackend.maxUnavailable`                | Max. percentage or number of pods allowed to be unavailable at a time              | `100%`                                                                    |
| `gitBackend.networkPolicy.ingress`         | Custom ingress-network policy for git-backend pods                                 | `[{}]` (allow all)                                                        |
| `gitBackend.networkPolicy.egress`          | Custom egress-network policy for git-backend pods                                  | `nil`                                                                     |
| `gitBackend.resources`                     | Configure the amount of resources the pod requests/is allowed                      | `requests.cpu: 100m`                                                      |
|                                            |                                                                                    | `requests.memory: 256Mi`                                                  |
|                                            |                                                                                    | `limits.cpu: 100m`                                                        |
|                                            |                                                                                    | `limits.memory: 256Mi`                                                    |
| `gitBackend.livenessProbe`                 | Configuration of the liveness probe timings                                        | `{initialDelaySeconds: 10, periodSeconds: 5}`                             |
| `gitBackend.readinessProbe`                | Configuration of the readiness probe timings                                       | `{initialDelaySeconds: 5, periodSeconds: 1}`                              |
| `gitBackend.credentials.htpasswd`          | `.htpasswd`-file containing username/password-credentials for accessing git        | `git:$apr1$O/LbLKC7$Q60GWE7OcqSEMSfe/K8xU.` (user: git, password: secret) |
| `gitBackend.service.type`                  | Which kind of Service to deploy                                                    | `LoadBalancer`                                                            |
| `gitBackend.service.http.enabled`          | Whether to serve HTTP-requests (needed for Ingress)                                | `true`                                                                    |
| `gitBackend.service.http.port`             | Port over which to expose HTTP                                                     | `80`                                                                      |
| `gitBackend.service.https.enabled`         | Whether to serve HTTPS-requests                                                    | `false`                                                                   |
| `gitBackend.service.https.port`            | Port over which to expose HTTPS                                                    | `443`                                                                     |

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

| Parameter                                      | Description                                                                                         | Default                                                                         |
|------------------------------------------------|-----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `gerritReplica.images.gerritInit`              | Image name of the Gerrit init container image                                                       | `k8s-gerrit/gerrit-init`                                                        |
| `gerritReplica.images.gerritReplica`           | Image name of the Gerrit replica container image                                                    | `k8s-gerrit/gerrit-replica`                                                     |
| `gerritReplica.replicas`                       | Number of pod replicas to deploy                                                                    | `1`                                                                             |
| `gerritReplica.maxSurge`                       | Max. percentage or number of pods allowed to be scheduled above the desired number                  | `25%`                                                                           |
| `gerritReplica.maxUnavailable`                 | Max. percentage or number of pods allowed to be unavailable at a time                               | `100%`                                                                          |
| `gerritReplica.livenessProbe`                  | Configuration of the liveness probe timings                                                         | `{initialDelaySeconds: 60, periodSeconds: 5}`                                   |
| `gerritReplica.readinessProbe`                 | Configuration of the readiness probe timings                                                        | `{initialDelaySeconds: 10, periodSeconds: 10}`                                  |
| `gerritReplica.startupProbe`                   | Configuration of the startup probe timings                                                          | `{initialDelaySeconds: 10, periodSeconds: 5}`                                   |
| `gerritReplica.resources`                      | Configure the amount of resources the pod requests/is allowed                                       | `requests.cpu: 1`                                                               |
|                                                |                                                                                                     | `requests.memory: 5Gi`                                                          |
|                                                |                                                                                                     | `limits.cpu: 1`                                                                 |
|                                                |                                                                                                     | `limits.memory: 6Gi`                                                            |
| `gerritReplica.networkPolicy.ingress`          | Custom ingress-network policy for gerrit-replica pods                                               | `nil`                                                                           |
| `gerritReplica.networkPolicy.egress`           | Custom egress-network policy for gerrit-replica pods                                                | `nil`                                                                           |
| `gerritReplica.service.type`                   | Which kind of Service to deploy                                                                     | `NodePort`                                                                      |
| `gerritReplica.service.http.port`              | Port over which to expose HTTP                                                                      | `80`                                                                            |
| `gerritReplica.service.ssh.enabled`            | Whether to enable SSH for the Gerrit replica                                                        | `false`                                                                         |
| `gerritReplica.service.ssh.port`               | Port for SSH                                                                                        | `29418`                                                                         |
| `gerritReplica.service.ssh.rsaKey`             | Private SSH key in RSA format                                                                       | `-----BEGIN RSA PRIVATE KEY-----`                                               |
| `gerritReplica.keystore`                       | base64-encoded Java keystore (`cat keystore.jks | base64`) to be used by Gerrit, when using SSL     | `nil`                                                                           |
| `gerritReplica.etc.config`                     | Map of config files (e.g. `gerrit.config`) that will be mounted to `$GERRIT_SITE/etc`by a ConfigMap | `{gerrit.config: ..., replication.config: ...}`[see here](#Gerrit-config-files) |
| `gerritReplica.etc.secret`                     | Map of config files (e.g. `secure.config`) that will be mounted to `$GERRIT_SITE/etc`by a Secret    | `{secure.config: ...}` [see here](#Gerrit-config-files)                         |
| `gerritReplica.additionalConfigMaps`           | Allows to mount additional ConfigMaps into a subdirectory of `$SITE/data`                           | `[]`                                                                            |
| `gerritReplica.additionalConfigMaps[*].name`   | Name of the ConfigMap                                                                               | `nil`                                                                           |
| `gerritReplica.additionalConfigMaps[*].subDir` | Subdirectory under `$SITE/data` into which the files should be symlinked                            | `nil`                                                                           |
| `gerritReplica.additionalConfigMaps[*].data`   | Data of the ConfigMap. If not set, secret has to be created manually                                | `nil`                                                                           |

### Gerrit config files

The gerrit-replica chart provides a ConfigMap containing the configuration files
used by Gerrit, e.g. `gerrit.config` and a Secret containing sensitive configuration
like the `secure.config` to configure the Gerrit installation in the Gerrit
component. The content of the config files can be set in the `values.yaml` under
the keys `gerritReplica.etc.config` and `gerritReplica.etc.secret` respectively.
The key has to be the filename (eg. `gerrit.config`) and the file's contents
the value. This way an arbitrary number of configuration files can be loaded into
the `$GERRIT_SITE/etc`-directory, e.g. for plugins.
All configuration options for Gerrit are described in detail in the
[official documentation of Gerrit](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html).
Some options however have to be set in a specified way for Gerrit to work as
intended with the chart:

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

To enable liveness- and readiness probes, the healthcheck plugin will be installed
by default. Note, that by configuring to use a packaged or downloaded version of
the healthcheck plugin, the configured version will take precedence over the default
version. The plugin is by default configured to disable the `querychanges` and
`auth` healthchecks, since the Gerrit replica does not index changes and a new
Gerrit server will not yet necessarily have an user to validate authentication.

The default configuration can be overwritten by adding the `healthcheck.config`
file as a key-value pair to `gerritReplica.etc.config` as for every other configuration.

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
