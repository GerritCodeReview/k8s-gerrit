# Gerrit Operator

## Build

To build all components of the operator run:

```sh
# With E2E tests
mvn clean install jib:dockerBuild
# Without E2E tests
mvn clean install -DskipTests jib:dockerBuild
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
- A valid default TLS certificate configured in the ingress controller

In addition, some properties have to be set to configure the tests:

- `rwmStorageClass`: Name of the StorageClass providing RWM-access (default:nfs-client)
- `registry`: Registry to pull container images from
- `RegistryOrg`: Organization of the container images
- `tag`: Container tag
- `registryUser`: User for the container registry
- `registryPwd`: Password for the container registry
- `ingressDomain`: Domain to be used for the ingress

The properties should be set in the `test.properties` file. ALternatively, a
path of a properties file can be configured by using
`mvn clean install -Dproperties=<path to properties file> $TARGET`

## Deploy

First all CustomResourceDefinitions have to be deployed:

```sh
kubectl apply -f target/classes/META-INF/fabric8/*-v1.yml
```

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
      ## If enabled, file ownership will be manually set, if a volume is mounted
      ## for the first time. (default: false)
      enabled: false

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

    ## Hostname to be used by the ingress. For each Gerrit deployment a new
    ## subdomain using the name of the respective Gerrit CustomResource will be
    ## used.
    host: example.com

    ## Annotations to be set for the ingress. This allows to configure the ingress
    ## further by e.g. setting the ingress class.
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
    sshPort: null

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
          basePath = git
          serverId = gerrit-1
          canonicalWebUrl = http://example.com/
          disableReverseDnsLookup = true
        [index]
          type = LUCENE
          onlineUpgrade = false
        [auth]
          type = DEVELOPMENT_BECOME_ANY_ACCOUNT
        [httpd]
          listenUrl = proxy-http://*:8080/
          requestLog = true
          gracefulStopTimeout = 1m
        [sshd]
          listenAddress = off
        [transfer]
          timeout = 120 s
        [user]
          name = Gerrit Code Review
          email = gerrit@example.com
          anonymousCoward = Unnamed User
        [cache]
          directory = cache
        [container]
          user = gerrit
          javaHome = /usr/lib/jvm/java-11-openjdk
          javaOptions = -Djavax.net.ssl.trustStore=/var/gerrit/etc/keystore
          javaOptions = -Xms200m
          javaOptions = -Xmx4g

  ## Names of secrets containing configuration files, e.g. secure.config, that
  ## will be mounted into the Gerrit site's etc-directory (optional)
  secrets: []
  # - gerrit-secure-config
```

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
