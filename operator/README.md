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

The tests require a Kubernetes cluster with a StorageClass that supports
ReadWriteMany access. The tests can be configured to use a storage class by
setting the rwmStorageClass property (default: nfs-client).

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
  ## secrets will be added to all pods.
  imagePullSecrets: []
  # - docker

  ## ImagePullPolicy (https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy)
  ## to be used in all containers
  imagePullPolicy: "Always"

  ## The container images in this project are tagged with the output of git describe.
  ## All container images are published for each version, even when the image itself
  ## was not updated. This ensures that all containers work well together.
  ## Here, the data on how to get those images can be configured.
  gerritImages:
    ## The registry from which to pull the images
    registry: "docker.io"

    ## The organization in the registry containing the images
    org: "k8sgerrit"

    ## The tag/version of the images
    tag: "latest"

  ## The busybox container is used for some init containers.
  busyBox:
    ## The registry from which to  pull the "busybox' image
    registry: docker.io

    ## The tag/version of the 'busybox' image
    tag: latest

  storageClasses:
    ## Name of a StorageClass allowing ReadWriteOnce access. (default: default)
    readWriteOnce: default

    ## Name of a StorageClass allowing ReadWriteMany access. (default: shared-storage)
    readWriteMany: nfs-client

    ## NFS is not well supported by Kubernetes. These options provide a workaround
    ## to ensure correct file ownership and id mapping
    nfsWorkaround:
      ## If enabled, file ownership will be manually set, if a volume is mounted
      ## for the first time.
      enabled: false

      ## The idmapd.config file can be used to e.g. configure the ID domain. This
      ## might be necessary for some NFS servers to ensure correct mapping of
      ## user and group IDs.
      idmapdConfig: |-
        [General]
          Verbosity = 0
          Domain = localdomain.com

        [Mapping]
          Nobody-User = nobody
          Nobody-Group = nogroup


  ## Storage for git repositories
  gitRepositoryStorage:
    ## Size of the volume (ReadWriteMany) used to store git repositories. (mandatory)
    size: 1Gi

    ## Name of a specific persistent volume to claim (optional)
    volumeName: git-repositories

    ## Selector (https://kubernetes.io/docs/concepts/storage/persistent-volumes/#selector)
    ## to select a specific persistent volume (optional)
    selector:
      matchLabels:
        volume-type: ssd
        aws-availability-zone: us-east-1

  ## Storage for logs
  gerritLogsStorage:
    ## Size of the volume (ReadWriteMany) used to store logs. (mandatory)
    size: 1Gi

    ## Name of a specific persistent volume to claim (optional)
    volumeName: logs

    ## Selector (https://kubernetes.io/docs/concepts/storage/persistent-volumes/#selector)
    ## to select a specific persistent volume (optional)
    selector:
      matchLabels:
        volume-type: ssd
        aws-availability-zone: us-east-1
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
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 100m
      memory: 256Mi

  ## Pod tolerations (https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/)
  tolerations: []
  # - key: "key1"
  #   operator: "Equal"
  #   value: "value1"
  #   effect: "NoSchedule"

  ## Pod affinity (https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes-using-node-affinity/)
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
