# Gerrit on Kubernetes

Gerrit is a web-based code review tool, which acts as a Git server. This helm
chart provides a Gerrit setup that can be deployed on Kubernetes.
In addition, the chart provides a CronJob to perform Git garbage collection.

***note
Gerrit versions before 3.0 are no longer supported, since the support of ReviewDB
was removed.
***

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
    [this guide](/helm-charts/gerrit-master/docs/nfs-provisioner.md) of how to
    deploy it in context of this project.

- A domain name that is configured to point to the IP address of the node running
  the Ingress controller on the kubernetes cluster (as described
  [here](http://alesnosek.com/blog/2017/02/14/accessing-kubernetes-pods-from-outside-of-the-cluster/)).

- (Optional: Required, if SSL is configured)
  A [Java keystore](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#httpd.sslKeyStore)
  to be used by Gerrit.

## Installing the Chart

***note
**ATTENTION:** The value for `gerritMaster.ingress.host` is required for rendering
the chart's templates. The nature of the value does not allow defaults.
Thus a custom `values.yaml`-file setting this value is required!
***

To install the chart with the release name `gerrit-master`, execute:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install ./gerrit-master \
  -n gerrit-master \
  -f <path-to-custom-values>.yaml
```

The command deploys the Gerrit instance on the current Kubernetes cluster.
The [configuration section](#Configuration) lists the parameters that can be
configured during installation.

## Configuration

The following sections list the configurable values in `values.yaml`. To configure
a Gerrit setup, make a copy of the `values.yaml`-file and change the parameters
as needed. The configuration can be applied by installing the chart as described
[above](#Installing-the-chart).

In addition, single options can be set without creating a custom `values.yaml`:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm install ./gerrit-master \
  -n gerrit-master \
  --set=gitRepositoryStorage.size=100Gi
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

| Parameter                               | Description                                     | Default              |
|-----------------------------------------|-------------------------------------------------|----------------------|
| `gitRepositoryStorage.externalPVC.use`  | Whether to use a PVC deployed outside the chart | `false`              |
| `gitRepositoryStorage.externalPVC.name` | Name of the external PVC                        | `git-filesystem-pvc` |
| `gitRepositoryStorage.size`             | Size of the volume storing the Git repositories | `5Gi`                |

If the git repositories should be persisted even if the chart is deleted and in
a way that the volume containing them can be mounted by the reinstalled chart,
the PVC claiming the volume has to be created independently of the chart. To use
the external PVC, set `gitRepositoryStorage.externalPVC.enabled` to `true` and
give the name of the PVC under `gitRepositoryStorage.externalPVC.name`.

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

### Gerrit

***note
The way the Jetty servlet used by Gerrit works, the Gerrit component of the
gerrit-master chart actually requires the URL to be known, when the chart is installed.
The suggested way to do that is to use the provided Ingress resource. This requires
that a URL is available and that the DNS is configured to point the URL to the
IP of the node the Ingress controller is running on!
***

***note
Setting the canonical web URL in the gerrit.config to the host used for the Ingress
is mandatory, if access to Gerrit is required!
***

| Parameter                                    | Description                                                                                     | Default                                                                                  |
|----------------------------------------------|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `gerritMaster.images.gerritInit`             | Image name of the Gerrit init container image                                                   | `k8s-gerrit/gerrit-init`                                                                 |
| `gerritMaster.images.gerritMaster`           | Image name of the Gerrit master container image                                                 | `k8s-gerrit/gerrit-master`                                                               |
| `gerritMaster.replicas`                      | Number of replica pods to deploy                                                                | `1`                                                                                      |
| `gerritMaster.updatePartition`               | Number of pods to update simultaneously                                                         | `1`                                                                                      |
| `gerritMaster.resources`                     | Configure the amount of resources the pod requests/is allowed                                   | `requests.cpu: 1`                                                                        |
|                                              |                                                                                                 | `requests.memory: 5Gi`                                                                   |
|                                              |                                                                                                 | `limits.cpu: 1`                                                                          |
|                                              |                                                                                                 | `limits.memory: 6Gi`                                                                     |
| `gerritMaster.persistence.enabled`           | Whether to persist the Gerrit site                                                              | `true`                                                                                   |
| `gerritMaster.persistence.size`              | Storage size for persisted Gerrit site                                                          | `10Gi`                                                                                   |
| `gerritMaster.service.type`                  | Which kind of Service to deploy                                                                 | `NodePort`                                                                               |
| `gerritMaster.service.http.port`             | Port over which to expose HTTP                                                                  | `80`                                                                                     |
| `gerritMaster.ingress.host`                  | REQUIRED: Host name to use for the Ingress (required for Ingress)                               | `nil`                                                                                    |
| `gerritMaster.ingress.additionalAnnotations` | Additional annotations for the Ingress                                                          | `nil`                                                                                    |
| `gerritMaster.ingress.tls.enabled`           | Whether to enable TLS termination in the Ingress                                                | `false`                                                                                  |
| `gerritMaster.ingress.tls.cert`              | Public SSL server certificate                                                                   | `-----BEGIN CERTIFICATE-----`                                                            |
| `gerritMaster.ingress.tls.key`               | Private SSL server certificate                                                                  | `-----BEGIN RSA PRIVATE KEY-----`                                                        |
| `gerritMaster.keystore`                      | base64-encoded Java keystore (`cat keystore.jks | base64`) to be used by Gerrit, when using SSL | `nil`                                                                                    |
| `gerritMaster.plugins.packaged`              | List of Gerrit plugins that are packaged into the Gerrit-war-file to install                    | `["commit-message-length-validator", "download-commands", "replication", "reviewnotes"]` |
| `gerritMaster.plugins.downloaded`            | List of Gerrit plugins that will be downloaded                                                  | `nil`                                                                                    |
| `gerritMaster.plugins.downloaded[0].name`    | Name of plugin                                                                                  | `nil`                                                                                    |
| `gerritMaster.plugins.downloaded[0].url`     | Download url of plugin                                                                          | `nil`                                                                                    |
| `gerritMaster.plugins.downloaded[0].sha1`    | SHA1 sum of plugin jar used to ensure file integrity and version (optional)                     | `nil`                                                                                    |
| `gerritMaster.plugins.cache.enabled`         | Whether to cache downloaded plugins                                                             | `false`                                                                                  |
| `gerritMaster.plugins.cache.size`            | Size of the volume used to store cached plugins                                                 | `1Gi`                                                                                    |
| `gerritMaster.config.gerrit`                 | The contents of the gerrit.config                                                               | [see here](#Gerrit-config-files)                                                         |
| `gerritMaster.config.secure`                 | The contents of the secure.config                                                               | [see here](#Gerrit-config-files)                                                         |
| `gerritMaster.config.replication`            | The contents of the replication.config                                                          | [see here](#Gerrit-config-files)                                                         |

### Gerrit config files

The gerrit-master chart provides a ConfigMap containing the `gerrit.config` as well
as `replication.config` and a Secret containing the `secure.config` to configure
the Gerrit installation in the Gerrit component. The content of the config files
can be set in the `values.yaml` under the keys `gerritMaster.config.gerrit`,
`gerritMaster.config.replication` and `gerritMaster.config.secure` respectively.
All configuration options are described in detail in the
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

    The technical user in the Gerrit container is called `gerrit`. Thus, this
    value is required to be `gerrit`.

- `container.javaHome`

    This has to be set to `/usr/lib/jvm/java-8-openjdk-amd64`, since this is
    the path of the Java installation in the container.

- `container.javaOptions`

    The maximum heap size has to be set. And its value has to be lower than the
    memory resource limit set for the container (e.g. `-Xmx4g`). In your calculation,
    allow memory for other components running in the container.

### Installing Gerrit plugins

There are several different ways to install plugins for Gerrit:

- **RECOMMENDED: Package the plugins to install into the WAR-file containing Gerrit.**
  This method provides the most stable way to install plugins, but requires to
  use a custom built gerrit-war file and container images, if plugins are required
  that are not part of the official `release.war`-file.

- **Download and cache plugins.** The chart supports downloading the plugin files and
  to cache them in a separate volume, that is shared between Gerrit-pods. SHA1-
  sums are used to validate plugin-files and versions.

- **Download plugins, but do not cache them.** This should only be used during
  development to save resources (the shared volume). Each pod will download the
  plugin-files on its own. Pods will fail to start up, if the download-URL is
  not valid anymore at some point in time.

## Upgrading the Chart

To upgrade an existing installation of the gerrit-master chart, e.g. to install
a newer chart version or to use an updated custom `values.yaml`-file, execute
the following command:

```sh
cd $(git rev-parse --show-toplevel)/helm-charts
helm upgrade <release-name> \
  -f <path-to-custom-values>.yaml \
  ./gerrit-master
```

## Uninstalling the Chart

To delete the chart from the cluster, use:

```sh
helm delete <release-name> \
  --purge
```
