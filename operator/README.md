# Gerrit Operator

## Build

To build all components of the operator run:

```sh
mvn clean install
```

## Publish

To publish the container image of the Gerrit Operator run:

```sh
mvn clean install -P publish
```

## Tests

Executing the E2E tests has a few infrastructure requirements that have to be
provided:

- An (unused) Kubernetes cluster
- The 'default' StorageClass that supports ReadWriteOnce access. It has to be
  possible to provision volumes using this StorageClass.
- A StorageClass that supports ReadWriteMany access. It has to be possible to
  provision volumes using this StorageClass. Such a StorageClass could be provided
  by the [NFS-subdir-provisioner chart](https://github.com/kubernetes-sigs/nfs-subdir-external-provisioner).
- An [Nginx Ingress Controller](https://github.com/kubernetes/ingress-nginx)
- An installation of [OpenLDAP](../supplements/test-cluster/ldap/openldap.yaml)
  with at least one user.
- Istio installed with the [profile](../istio/gerrit.profile.yaml) provided by
  this project
- A secret containing valid certificates for the given hostnames. For istio this
  secret has to be named `tls-secret` and be present in the `istio-system` namespace.
  For the Ingress controller, the secret has to be either set as the default
  secret to be used or somehow automatically be provided in the namespaces created
  by the tests and named `tls-secret`, e.g. by using Gardener to manage DNS and
  certificates.

A sample setup for components required in the cluster is provided under
`$REPO_ROOT/supplements/test-cluster`. Some configuration has to be done manually
(marked by `#TODO`) and the `deploy.sh`-script can be used to install/update all
components.

In addition, some properties have to be set to configure the tests:

- `rwmStorageClass`: Name of the StorageClass providing RWM-access (default:nfs-client)
- `registry`: Registry to pull container images from
- `RegistryOrg`: Organization of the container images
- `tag`: Container tag
- `registryUser`: User for the container registry
- `registryPwd`: Password for the container registry
- `ingressDomain`: Domain to be used for the ingress
- `istioDomain`: Domain to be used for istio
- `ldapAdminPwd`: Admin password for LDAP server
- `gerritUser`: Username of a user in LDAP
- `gerritPwd`: The password of `gerritUser`

The properties should be set in the `test.properties` file. Alternatively, a
path of a properties file can be configured by using the
`-Dproperties=<path to properties file>`-option.

To run all E2E tests, use:

```sh
mvn clean install -P integration-test -Dproperties=<path to properties file>
```

Note, that running the E2E tests will also involve pushing the container image
to the repository configured in the properties file.

## Deploy

First all CustomResourceDefinitions have to be deployed:

```sh
kubectl apply -f target/classes/META-INF/fabric8/*-v1.yml
```

Note that these do not include the -v1beta1.yaml files, as those are for old
Kubernetes versions.

The operator requires a Java Keystore with a keypair inside to allow TLS
verification for Kubernetes Admission Webhooks. To create a keystore and
encode it with base64, run:

```sh
keytool \
  -genkeypair \
  -alias operator \
  -keystore keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650
cat keystore | base64 -b 0
```

Add the result to the Secret in `k8s/operator.yaml` (see comments in the file)
and also add the base64-encoded password for the keystore to the secret.

Then the operator and associated RBAC rules can be deployed:

```sh
kubectl apply -f k8s/operator.yaml
```

`k8s/operator.yaml` contains a basic deployment of the operator. Resources,
docker image name etc. might have to be adapted.

## Install custom resources

### GerritCluster

The GerritCluster custom resource configures and provisions resources shared by
multiple components in a Gerrit cluster. A cluster is meant to be run in a single
namespace and only one cluster per namespace is allowed.

An example CustomResource is provided at `k8s/cluster.sample.yaml`. To install
it into the cluster run:

```sh
kubectl apply -f k8s/cluster.sample.yaml
```

### Gerrit

**NOTE:** A primary Gerrit should never be installed in the same GerritCluster as a
Receiver to avoid conflicts when writing into repositories.

An example of a Gerrit-CustomResource can be found at `k8s/gerrit.sample.yaml`.
To install it into the cluster run:

```sh
kubectl apply -f k8s/gerrit.sample.yaml
```

The operator will create all resources to run a primary Gerrit.

### GitGarbageCollection

An example of a GitGc-CustomResource can be found at `k8s/gitgc.sample.yaml`.
To install it into the cluster run:

```sh
kubectl apply -f k8s/gitgc.sample.yaml
```

The operator will create a CronJob based on the provided spec.

### Receiver

**NOTE:** A Receiver should never be installed in the same GerritCluster as a
primary Gerrit to avoid conflicts when writing into repositories.

An example of a Receiver-CustomResource can be found at `k8s/receiver.sample.yaml`.
To install it into the cluster run:

```sh
kubectl apply -f k8s/receiver.sample.yaml
```

The operator will create all resources to run a receiver for push replication
requests.

## Configure custom resources

### GerritCluster

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: GerritCluster
metadata:
  name: gerrit
spec:
  ## List of names representing imagePullSecrets available in the cluster. These
  ## secrets will be added to all pods. (optional)
  imagePullSecrets: []
  # - name: docker

  ## ImagePullPolicy (https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy)
  ## to be used in all containers. (default: Always)
  imagePullPolicy: "Always"

  ## The container images in this project are tagged with the output of git describe.
  ## All container images are published for each version, even when the image itself
  ## was not updated. This ensures that all containers work well together.
  ## Here, the data on how to get those images can be configured.
  gerritImages:
    ## The registry from which to pull the images. (default: docker.io)
    registry: "docker.io"

    ## The organization in the registry containing the images. (default: k8sgerrit)
    org: "k8sgerrit"

    ## The tag/version of the images. (default: latest)
    tag: "latest"

  ## The busybox container is used for some init containers.
  busyBox:
    ## The registry from which to  pull the "busybox' image. (default: docker.io)
    registry: docker.io

    ## The tag/version of the 'busybox' image. (default: latest)
    tag: latest

  storageClasses:
    ## Name of a StorageClass allowing ReadWriteOnce access. (default: default)
    readWriteOnce: default

    ## Name of a StorageClass allowing ReadWriteMany access. (default: shared-storage)
    readWriteMany: shared-storage

    ## NFS is not well supported by Kubernetes. These options provide a workaround
    ## to ensure correct file ownership and id mapping
    nfsWorkaround:
      ## If enabled, below options might be used. (default: false)
      enabled: false

      ## If enabled, the ownership of the mounted NFS volumes will be set on pod
      ## startup. Note that this is not done recursively. It is expected that all
      ## data already present in the volume was created by the user used in accessing
      ## containers. (default: false)
      chownOnStartup: false

      ## The idmapd.config file can be used to e.g. configure the ID domain. This
      ## might be necessary for some NFS servers to ensure correct mapping of
      ## user and group IDs. (optional)
      idmapdConfig: ""
        # [General]
        #   Verbosity = 0
        #   Domain = localdomain.com

        # [Mapping]
        #   Nobody-User = nobody
        #   Nobody-Group = nogroup


  ## Storage for git repositories
  gitRepositoryStorage:
    ## Size of the volume (ReadWriteMany) used to store git repositories. (mandatory)
    size: 1Gi

    ## Name of a specific persistent volume to claim (optional)
    volumeName: ""

    ## Selector (https://kubernetes.io/docs/concepts/storage/persistent-volumes/#selector)
    ## to select a specific persistent volume (optional)
    selector: null
      # matchLabels:
      #   volume-type: ssd
      #   aws-availability-zone: us-east-1

  ## Storage for logs
  logsStorage:
    ## Size of the volume (ReadWriteMany) used to store logs. (mandatory)
    size: 1Gi

    ## Name of a specific persistent volume to claim (optional)
    volumeName: ""

    ## Selector (https://kubernetes.io/docs/concepts/storage/persistent-volumes/#selector)
    ## to select a specific persistent volume (optional)
    selector: null
      # matchLabels:
      #   volume-type: ssd
      #   aws-availability-zone: us-east-1

  ## Configuration for an ingress that will be used to route ingress traffic to
  ## all exposed applications within the Gerrit cluster.
  ingress:
    ## Whether to provision an Ingress.
    enabled: true

    ## Which type of Ingress provider to use. Either: NONE, INGRESS or ISTIO
    ## (default: NONE)
    type: NONE

    ## Hostname to be used by the ingress. For each Gerrit deployment a new
    ## subdomain using the name of the respective Gerrit CustomResource will be
    ## used.
    host: example.com

    ## Annotations to be set for the ingress. This allows to configure the ingress
    ## further by e.g. setting the ingress class. This will be only used for type
    ## INGRESS and ignored otherwise. (optional)
    annotations: {}

    ## Configuration of TLS to be used in the ingress
    tls:
      ## Whether to use TLS
      enabled: false

      ## Name of the secret containing the TLS key pair. The certificate should
      ## be a wildcard certificate allowing for all subdomains under the given
      ## host.
      secret: ""
```

### Gerrit

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: Gerrit
metadata:
  name: gerrit
spec:
  ## Name of the Gerrit cluster this Gerrit is a part of. (mandatory)
  cluster: gerrit

  ## Pod tolerations (https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/)
  ## (optional)
  tolerations: []
  # - key: "key1"
  #   operator: "Equal"
  #   value: "value1"
  #   effect: "NoSchedule"

  ## Pod affinity (https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes-using-node-affinity/)
  ## (optional)
  affinity: {}
    # nodeAffinity:
    # requiredDuringSchedulingIgnoredDuringExecution:
    #   nodeSelectorTerms:
    #   - matchExpressions:
    #     - key: disktype
    #       operator: In
    #       values:
    #       - ssd

  ## Pod topology spread constraints (https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/#:~:text=You%20can%20use%20topology%20spread,well%20as%20efficient%20resource%20utilization.)
  ## (optional)
  topologySpreadConstraints: []
  # - maxSkew: 1
  #   topologyKey: zone
  #   whenUnsatisfiable: DoNotSchedule
  #   labelSelector:
  #     matchLabels:
  #       foo: bar

  ## PriorityClass to be used with the pod (https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)
  ## (optional)
  priorityClassName: ""

  ## Number of pods running Gerrit in the StatefulSet (default: 1)
  replicas: 1

  ## Ordinal at which to start updating pods. Pods with a lower ordinal will not be updated. (default: 0)
  updatePartition: 0

  ## Resource requests/limits of the gerrit container
  ## (https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
  ## (optional)
  resources: {}
    # requests:
    #   cpu: 1
    #   memory: 5Gi
    # limits:
    #   cpu: 1
    #   memory: 6Gi

  ## Startup probe (https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
  ## The action will be set by the operator. All other probe parameters can be set.
  ## (optional)
  startupProbe: {}
    # initialDelaySeconds: 0
    # periodSeconds: 10
    # timeoutSeconds: 1
    # successThreshold: 1
    # failureThreshold: 3

  ## Readiness probe (https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
  ## The action will be set by the operator. All other probe parameters can be set.
  ## (optional)
  readinessProbe: {}
    # initialDelaySeconds: 0
    # periodSeconds: 10
    # timeoutSeconds: 1
    # successThreshold: 1
    # failureThreshold: 3

  ## Liveness probe (https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
  ## The action will be set by the operator. All other probe parameters can be set.
  ## (optional)
  livenessProbe: {}
    # initialDelaySeconds: 0
    # periodSeconds: 10
    # timeoutSeconds: 1
    # successThreshold: 1
    # failureThreshold: 3

  ## Seconds the pod is allowed to shutdown until it is forcefully killed (default: 30)
  gracefulStopTimeout: 30

  ## Configuration for the service used to manage network access to the StatefulSet
  service:
    ## Service type (default: NodePort)
    type: NodePort

    ## Port used for HTTP requests (default: 80)
    httpPort: 80

    ## Port used for SSH requests (optional; if unset, SSH access is disabled)
    ## If Istio is used, the Gateway will be automatically configured to accept
    ## SSH requests. If an Ingress controller is used, SSH requests will only be
    ## served by the Service itself!
    sshPort: null

  ## Whether to run Gerrit in replica mode
  isReplica: true

  ## Configuration concerning the Gerrit site
  site:
    ## Size of the volume used to persist not otherwise persisted site components
    ## (e.g. git repositories are persisted in a dedicated volume) (mandatory)
    size: 1Gi

  ## List of Gerrit plugins to install. These plugins can either be packaged in
  ## the Gerrit war-file or they will be downloaded. (optional)
  plugins: []
  ## Installs a packaged plugin
  # - name: delete-project

  ## Downloads and installs a plugin
  # - name: javamelody
  #   url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-javamelody-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar
  #   sha1: 40ffcd00263171e373a24eb6a311791b2924707c

  ## If the `installAsLibrary` option is set to `true` the plugin's jar-file will
  ## be symlinked to the lib directory and thus installed as a library as well.
  # - name: saml
  #   url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-saml-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/saml/saml.jar
  #   sha1: 6dfe8292d46b179638586e6acf671206f4e0a88b
  #   installAsLibrary: true

  ## Configuration files for Gerrit that will be mounted into the Gerrit site's
  ## etc-directory (gerrit.config is mandatory)
  configFiles:
    gerrit.config: |-
        [gerrit]
          serverId = gerrit-1
          disableReverseDnsLookup = true
        [index]
          type = LUCENE
        [auth]
          type = DEVELOPMENT_BECOME_ANY_ACCOUNT
        [httpd]
          requestLog = true
          gracefulStopTimeout = 1m
        [transfer]
          timeout = 120 s
        [user]
          name = Gerrit Code Review
          email = gerrit@example.com
          anonymousCoward = Unnamed User
        [container]
          javaOptions = -Xms200m
          javaOptions = -Xmx4g

  ## Names of secrets containing configuration files, e.g. secure.config, that
  ## will be mounted into the Gerrit site's etc-directory (optional)
  secrets: []
  # - gerrit-secure-config
```

#### Prohibited options in gerrit.config

Some options in the gerrit.config are not allowed to be changed. Their values
are preset by the containers/Kubernetes. The operator will configure those options
automatically and won't allow different values, i.e. it will fail to reconcile
if a value is set to an illegal value. These options are:

- `cache.directory`

    This should stay in the volume mounted to contain the Gerrit site and will
    thus be set to `cache`.

- `container.javaHome`

    This has to be set to `/usr/lib/jvm/java-11-openjdk-amd64`, since this is
    the path of the Java installation in the container.

- `container.javaOptions = -Djavax.net.ssl.trustStore`

    The keystore will be mounted to `/var/gerrit/etc/keystore`.

- `container.replica`

    This has to be set in the Gerrit-CustomResource under `spec.isReplica`.

- `container.user`

    The technical user in the Gerrit container is called `gerrit`.

- `gerrit.basePath`

    The git repositories are mounted to `/var/gerrit/git` in the container.

- `gerrit.canonicalWebUrl`

    The canonical web URL has to be set to the hostname used by the Ingress/Istio.

- `httpd.listenURL`

    This has to be set to `proxy-http://*:8080/` or `proxy-https://*:8080`,
    depending of TLS is enabled in the Ingress or not, otherwise the Jetty
    servlet will run into an endless redirect loop.

- `sshd.advertisedAddress`

    This is only enforced, if Istio is enabled. It can be configured otherwise.

- `sshd.listenAddress`

    Since the container port for SSH is fixed, this will be set automatically.
    If no SSH port is configured in the service, the SSHD is disabled.

### GitGarbageCollection

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: GitGarbageCollection
metadata:
  name: gitgc
spec:
  ## Name of the Gerrit cluster this GitGc is a part of. (mandatory)
  cluster: gerrit

  ## Cron schedule defining when to run git gc (mandatory)
  schedule: "*/5 * * * *"

  ## List of projects to gc. If omitted, all projects not handled by other Git GC
  ## jobs will be gc'ed. Only one job gc'ing all projects can exist. (default: [])
  projects: []
  # - All-Projects
  # - All-Users

  ## Resource requests/limits of the git gc container
  ## (https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
  ## (optional)
  resources: {}
    # requests:
    #   cpu: 100m
    #   memory: 256Mi
    # limits:
    #   cpu: 100m
    #   memory: 256Mi

  ## Pod tolerations (https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/)
  ## (optional)
  tolerations: []
  # - key: "key1"
  #   operator: "Equal"
  #   value: "value1"
  #   effect: "NoSchedule"

  ## Pod affinity (https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes-using-node-affinity/)
  ## (optional)
  affinity: {}
    # nodeAffinity:
    # requiredDuringSchedulingIgnoredDuringExecution:
    #   nodeSelectorTerms:
    #   - matchExpressions:
    #     - key: disktype
    #       operator: In
    #       values:
    #       - ssd
```

### Receiver

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: Receiver
metadata:
  name: receiver
spec:
  ## Name of the Gerrit cluster this Gerrit is a part of. (mandatory)
  cluster: gerrit

  ## Pod tolerations (https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/)
  ## (optional)
  tolerations: []
  # - key: "key1"
  #   operator: "Equal"
  #   value: "value1"
  #   effect: "NoSchedule"

  ## Pod affinity (https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes-using-node-affinity/)
  ## (optional)
  affinity: {}
    # nodeAffinity:
    # requiredDuringSchedulingIgnoredDuringExecution:
    #   nodeSelectorTerms:
    #   - matchExpressions:
    #     - key: disktype
    #       operator: In
    #       values:
    #       - ssd

  ## Pod topology spread constraints (https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/#:~:text=You%20can%20use%20topology%20spread,well%20as%20efficient%20resource%20utilization.)
  ## (optional)
  topologySpreadConstraints: []
  # - maxSkew: 1
  #   topologyKey: zone
  #   whenUnsatisfiable: DoNotSchedule
  #   labelSelector:
  #     matchLabels:
  #       foo: bar

  ## PriorityClass to be used with the pod (https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)
  ## (optional)
  priorityClassName: ""

  ## Number of pods running Gerrit in the StatefulSet (default: 1)
  replicas: 1

  ## Ordinal or percentage of pods that are allowed to be created in addition during
  ## rolling updates. (default: 1)
  maxSurge: 1

  ## Ordinal or percentage of pods that are allowed to be unavailable during
  ## rolling updates. (default: 1)
  maxUnavailable: 1

  ## Resource requests/limits of the receiver container
  ## (https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
  ## (optional)
  resources: {}
    # requests:
    #   cpu: 1
    #   memory: 5Gi
    # limits:
    #   cpu: 1
    #   memory: 6Gi

  ## Readiness probe (https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
  ## The action will be set by the operator. All other probe parameters can be set.
  ## (optional)
  readinessProbe: {}
    # initialDelaySeconds: 0
    # periodSeconds: 10
    # timeoutSeconds: 1
    # successThreshold: 1
    # failureThreshold: 3

  ## Liveness probe (https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
  ## The action will be set by the operator. All other probe parameters can be set.
  ## (optional)
  livenessProbe: {}
    # initialDelaySeconds: 0
    # periodSeconds: 10
    # timeoutSeconds: 1
    # successThreshold: 1
    # failureThreshold: 3

  ## Configuration for the service used to manage network access to the StatefulSet
  service:
    ## Service type (default: NodePort)
    type: NodePort

    ## Port used for HTTP requests (default: 80)
    httpPort: 80

  ## Name of the secret containing the .htpasswd file used to configure basic
  ## authentication within the Apache server (mandatory)
  credentialSecretRef: null
```
