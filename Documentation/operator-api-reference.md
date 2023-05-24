# Gerrit Operator - API Reference

1. [Gerrit Operator - API Reference](#gerrit-operator---api-reference)
   1. [General Remarks](#general-remarks)
      1. [Inheritance](#inheritance)
   2. [GerritCluster](#gerritcluster)
   3. [Gerrit](#gerrit)
   4. [Receiver](#receiver)
   5. [GitGarbageCollection](#gitgarbagecollection)
   6. [GerritClusterSpec](#gerritclusterspec)
   7. [GerritClusterStatus](#gerritclusterstatus)
   8. [GerritStorageConfig](#gerritstorageconfig)
   9. [StorageClassConfig](#storageclassconfig)
   10. [NfsWorkaroundConfig](#nfsworkaroundconfig)
   11. [SharedStorage](#sharedstorage)
   12. [OptionalSharedStorage](#optionalsharedstorage)
   13. [ContainerImageConfig](#containerimageconfig)
   14. [BusyBoxImage](#busyboximage)
   15. [GerritRepositoryConfig](#gerritrepositoryconfig)
   16. [GerritClusterIngressConfig](#gerritclusteringressconfig)
   17. [IngressType](#ingresstype)
   18. [GerritIngressTlsConfig](#gerritingresstlsconfig)
   19. [GerritTemplate](#gerrittemplate)
   20. [GerritTemplateSpec](#gerrittemplatespec)
   21. [GerritProbe](#gerritprobe)
   22. [GerritServiceConfig](#gerritserviceconfig)
   23. [GerritSite](#gerritsite)
   24. [GerritPlugin](#gerritplugin)
   25. [GerritMode](#gerritmode)
   26. [GerritSpec](#gerritspec)
   27. [GerritStatus](#gerritstatus)
   28. [IngressConfig](#ingressconfig)
   29. [ReceiverTemplate](#receivertemplate)
   30. [ReceiverTemplateSpec](#receivertemplatespec)
   31. [ReceiverSpec](#receiverspec)
   32. [ReceiverStatus](#receiverstatus)
   33. [ReceiverProbe](#receiverprobe)
   34. [ReceiverServiceConfig](#receiverserviceconfig)
   35. [GitGarbageCollectionSpec](#gitgarbagecollectionspec)
   36. [GitGarbageCollectionStatus](#gitgarbagecollectionstatus)
   37. [GitGcState](#gitgcstate)

## General Remarks

### Inheritance

Some objects inherit the fields of other objects. In this case the section will
contain an **Extends:** label to link to the parent object, but it will not repeat
inherited fields.

## GerritCluster

---

**Group**: gerritoperator.google.com \
**Version**: v1alpha2 \
**Kind**: GerritCluster

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`GerritClusterSpec`](#gerritclusterspec) | Specification for GerritCluster |
| `status` | [`GerritClusterStatus`](#gerritclusterstatus) | Status for GerritCluster |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1alpha2"
kind: GerritCluster
metadata:
  name: gerrit
spec:
  containerImages:
    imagePullSecrets: []
    imagePullPolicy: Always
    gerritImages:
      registry: docker.io
      org: k8sgerrit
      tag: latest
    busyBox:
      registry: docker.io
      tag: latest

  storage:
    storageClasses:
      readWriteOnce: default
      readWriteMany: shared-storage
      nfsWorkaround:
        enabled: false
        chownOnStartup: false
        idmapdConfig: |-
          [General]
            Verbosity = 0
            Domain = localdomain.com

          [Mapping]
            Nobody-User = nobody
            Nobody-Group = nogroup

    gitRepositoryStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    logsStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    pluginCacheStorage:
      enabled: false
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

  ingress:
    enabled: true
    type: INGRESS
    host: example.com
    annotations: {}
    tls:
      enabled: false
      secret: ""

  gerrits:
  - metadata:
      name: gerrit
      labels:
        app: gerrit
    spec:
      tolerations:
      - key: key1
        operator: Equal
        value: value1
        effect: NoSchedule

      affinity:
        nodeAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
          nodeSelectorTerms:
          - matchExpressions:
            - key: disktype
              operator: In
              values:
              - ssd

      topologySpreadConstraints: []
      - maxSkew: 1
        topologyKey: zone
        whenUnsatisfiable: DoNotSchedule
        labelSelector:
          matchLabels:
            foo: bar

      priorityClassName: ""

      replicas: 1
      updatePartition: 0

      resources:
        requests:
          cpu: 1
          memory: 5Gi
        limits:
          cpu: 1
          memory: 6Gi

      startupProbe:
        initialDelaySeconds: 0
        periodSeconds: 10
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 3

      readinessProbe:
        initialDelaySeconds: 0
        periodSeconds: 10
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 3

      livenessProbe:
        initialDelaySeconds: 0
        periodSeconds: 10
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 3

      gracefulStopTimeout: 30

      service:
        type: NodePort
        httpPort: 80
        sshPort: 29418

      mode: REPLICA

      site:
        size: 1Gi

      plugins:
      # Installs a packaged plugin
      - name: delete-project

      # Downloads and installs a plugin
      - name: javamelody
        url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-javamelody-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar
        sha1: 40ffcd00263171e373a24eb6a311791b2924707c

      # If the `installAsLibrary` option is set to `true` the plugin's jar-file will
      # be symlinked to the lib directory and thus installed as a library as well.
      - name: saml
        url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-saml-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/saml/saml.jar
        sha1: 6dfe8292d46b179638586e6acf671206f4e0a88b
        installAsLibrary: true

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

      secrets:
      - gerrit-secure-config

  receiver:
    metadata:
      name: receiver
      labels:
        app: receiver
    spec:
      tolerations:
      - key: key1
        operator: Equal
        value: value2
        effect: NoSchedule

      affinity:
        nodeAffinity:
        requiredDuringSchedulingIgnoredDuringExecution:
          nodeSelectorTerms:
          - matchExpressions:
            - key: disktype
              operator: In
              values:
              - ssd

      topologySpreadConstraints: []
      - maxSkew: 1
        topologyKey: zone
        whenUnsatisfiable: DoNotSchedule
        labelSelector:
          matchLabels:
            foo: bar

      priorityClassName: ""

      replicas: 2
      maxSurge: 1
      maxUnavailable: 1

      resources:
        requests:
          cpu: 1
          memory: 5Gi
        limits:
          cpu: 1
          memory: 6Gi

      readinessProbe:
        initialDelaySeconds: 0
        periodSeconds: 10
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 3

      livenessProbe:
        initialDelaySeconds: 0
        periodSeconds: 10
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 3

      service:
        type: NodePort
        httpPort: 80

      credentialSecretRef: receiver-credentials
```

## Gerrit

---

**Group**: gerritoperator.google.com \
**Version**: v1alpha2 \
**Kind**: Gerrit

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`GerritSpec`](#gerritspec) | Specification for Gerrit |
| `status` | [`GerritStatus`](#gerritstatus) | Status for Gerrit |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1alpha2"
kind: Gerrit
metadata:
  name: gerrit
spec:
  tolerations:
    - key: key1
      operator: Equal
      value: value1
      effect: NoSchedule

    affinity:
      nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: disktype
            operator: In
            values:
            - ssd

    topologySpreadConstraints: []
    - maxSkew: 1
      topologyKey: zone
      whenUnsatisfiable: DoNotSchedule
      labelSelector:
        matchLabels:
          foo: bar

    priorityClassName: ""

    replicas: 1
    updatePartition: 0

    resources:
      requests:
        cpu: 1
        memory: 5Gi
      limits:
        cpu: 1
        memory: 6Gi

    startupProbe:
      initialDelaySeconds: 0
      periodSeconds: 10
      timeoutSeconds: 1
      successThreshold: 1
      failureThreshold: 3

    readinessProbe:
      initialDelaySeconds: 0
      periodSeconds: 10
      timeoutSeconds: 1
      successThreshold: 1
      failureThreshold: 3

    livenessProbe:
      initialDelaySeconds: 0
      periodSeconds: 10
      timeoutSeconds: 1
      successThreshold: 1
      failureThreshold: 3

    gracefulStopTimeout: 30

    service:
      type: NodePort
      httpPort: 80
      sshPort: 29418

    mode: PRIMARY

    site:
      size: 1Gi

    plugins:
    # Installs a packaged plugin
    - name: delete-project

    # Downloads and installs a plugin
    - name: javamelody
      url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-javamelody-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar
      sha1: 40ffcd00263171e373a24eb6a311791b2924707c

    # If the `installAsLibrary` option is set to `true` the plugin's jar-file will
    # be symlinked to the lib directory and thus installed as a library as well.
    - name: saml
      url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-saml-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/saml/saml.jar
      sha1: 6dfe8292d46b179638586e6acf671206f4e0a88b
      installAsLibrary: true

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

    secrets:
    - gerrit-secure-config

  containerImages:
    imagePullSecrets: []
    imagePullPolicy: Always
    gerritImages:
      registry: docker.io
      org: k8sgerrit
      tag: latest
    busyBox:
      registry: docker.io
      tag: latest

  storage:
    storageClasses:
      readWriteOnce: default
      readWriteMany: shared-storage
      nfsWorkaround:
        enabled: false
        chownOnStartup: false
        idmapdConfig: |-
          [General]
            Verbosity = 0
            Domain = localdomain.com

          [Mapping]
            Nobody-User = nobody
            Nobody-Group = nogroup

    gitRepositoryStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    logsStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    pluginCacheStorage:
      enabled: false
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

  ingress:
    type: INGRESS
    host: example.com
    tlsEnabled: false
```

## Receiver

---

**Group**: gerritoperator.google.com \
**Version**: v1alpha1 \
**Kind**: Receiver

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`ReceiverSpec`](#receiverspec) | Specification for Receiver |
| `status` | [`ReceiverStatus`](#receiverstatus) | Status for Receiver |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: Receiver
metadata:
  name: receiver
spec:
  cluster: gerrit
  tolerations:
  - key: key1
    operator: Equal
    value: value1
    effect: NoSchedule

  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: disktype
            operator: In
            values:
            - ssd

  topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        foo: bar

  priorityClassName: ""

  replicas: 1
  maxSurge: 1
  maxUnavailable: 1

  resources: {}
    requests:
      cpu: 1
      memory: 5Gi
    limits:
      cpu: 1
      memory: 6Gi

  readinessProbe:
    initialDelaySeconds: 0
    periodSeconds: 10
    timeoutSeconds: 1
    successThreshold: 1
    failureThreshold: 3

  livenessProbe:
    initialDelaySeconds: 0
    periodSeconds: 10
    timeoutSeconds: 1
    successThreshold: 1
    failureThreshold: 3

  service:
    type: NodePort
    httpPort: 80

  credentialSecretRef: apache-credentials

  containerImages:
    imagePullSecrets: []
    imagePullPolicy: Always
    gerritImages:
      registry: docker.io
      org: k8sgerrit
      tag: latest
    busyBox:
      registry: docker.io
      tag: latest

  storage:
    storageClasses:
      readWriteOnce: default
      readWriteMany: shared-storage
      nfsWorkaround:
        enabled: false
        chownOnStartup: false
        idmapdConfig: |-
          [General]
            Verbosity = 0
            Domain = localdomain.com

          [Mapping]
            Nobody-User = nobody
            Nobody-Group = nogroup

    gitRepositoryStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    logsStorage:
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

  ingress:
    type: INGRESS
    host: example.com
    tlsEnabled: false
```

## GitGarbageCollection

---

**Group**: gerritoperator.google.com \
**Version**: v1alpha1 \
**Kind**: GitGarbageCollection

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`GitGarbageCollectionSpec`](#gitgarbagecollectionspec) | Specification for GitGarbageCollection |
| `status` | [`GitGarbageCollectionStatus`](#gitgarbagecollectionstatus) | Status for GitGarbageCollection |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: GitGarbageCollection
metadata:
  name: gitgc
spec:
  cluster: gerrit
  schedule: "*/5 * * * *"

  projects: []

  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 100m
      memory: 256Mi

  tolerations:
  - key: key1
    operator: Equal
    value: value1
    effect: NoSchedule

  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: disktype
            operator: In
            values:
            - ssd
```

## GerritClusterSpec

| Field | Type | Description |
|---|---|---|
| `storage` | [`GerritStorageConfig`](#gerritstorageconfig) | Storage used by Gerrit instances |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |
| `ingress` | [`GerritClusterIngressConfig`](#gerritclusteringressconfig) | Ingress traffic handling in GerritCluster |
| `gerrits` | [`GerritTemplate`](#gerrittemplate)-Array | A list of Gerrit instances to be installed in the GerritCluster. Only a single primary Gerrit is permitted. |
| `receiver` | [`ReceiverTemplate`](#receivertemplate) | A Receiver instance to be installed in the GerritCluster. |

## GerritClusterStatus

| Field | Type | Description |
|---|---|---|
| `members` | `Map<String, List<String>>` | A map listing all Gerrit and Receiver instances managed by the GerritCluster by name |

## GerritStorageConfig

| Field | Type | Description |
|---|---|---|
| `storageClasses` | [`StorageClassConfig`](#storageclassconfig) | StorageClasses used in the GerritCluster |
| `gitRepositoryStorage` | [`SharedStorage`](#sharedstorage) | Volume used for storing Git repositories |
| `logsStorage` | [`SharedStorage`](#sharedstorage) | Volume used for storing logs |
| `pluginCacheStorage` | [`OptionalSharedStorage`](#optionalsharedstorage) | Volume used for caching downloaded plugin JAR-files (Only used by Gerrit resources. Otherwise ignored.) |

## StorageClassConfig

| Field | Type | Description |
|---|---|---|
| `readWriteOnce` | `String` | Name of a StorageClass allowing ReadWriteOnce access. (default: `default`) |
| `readWriteMany` | `String` | Name of a StorageClass allowing ReadWriteMany access. (default: `shared-storage`) |
| `nfsWorkaround` | [`NfsWorkaroundConfig`](#nfsworkaroundconfig) | NFS is not well supported by Kubernetes. These options provide a workaround to ensure correct file ownership and id mapping |

## NfsWorkaroundConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | If enabled, below options might be used. (default: `false`) |
| `chownOnStartup` | `boolean` | If enabled, the ownership of the mounted NFS volumes will be set on pod startup. Note that this is not done recursively. It is expected that all data already present in the volume was created by the user used in accessing containers. (default: `false`) |
| `idmapdConfig` | `String` | The idmapd.config file can be used to e.g. configure the ID domain. This might be necessary for some NFS servers to ensure correct mapping of user and group IDs. (optional) |

## SharedStorage

| Field | Type | Description |
|---|---|---|
| `size` | [`Quantity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#quantity-resource-core) | Size of the volume (mandatory) |
| `volumeName` | `String` | Name of a specific persistent volume to claim (optional) |
| `selector` | [`LabelSelector`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#labelselector-v1-meta) | Selector to select a specific persistent volume (optional) |

## OptionalSharedStorage

**Extends:** [`SharedStorage`](#sharedstorage)

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether to enable this storage. (default: `false`) |

## ContainerImageConfig

| Field | Type | Description |
|---|---|---|
| `imagePullPolicy` | `String` | Image pull policy (https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy) to be used in all containers. (default: `Always`) |
| `imagePullSecrets` | [`LocalObjectReference`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#localobjectreference-v1-core)-Array | List of names representing imagePullSecrets available in the cluster. These secrets will be added to all pods. (optional) |
| `busyBox` | [`BusyBoxImage`](#busyboximage) | The busybox container is used for some init containers |
| `gerritImages` | [`GerritRepositoryConfig`](#gerritrepositoryconfig) | The container images in this project are tagged with the output of git describe. All container images are published for each version, even when the image itself was not updated. This ensures that all containers work well together. Here, the data on how to get those images can be configured. |

## BusyBoxImage

| Field | Type | Description |
|---|---|---|
| `registry` | `String` | The registry from which to pull the "busybox" image. (default: `docker.io`) |
| `tag` | `String` | The tag/version of the "busybox" image. (default: `latest`) |

## GerritRepositoryConfig

| Field | Type | Description |
|---|---|---|
| `registry` | `String` | The registry from which to pull the images. (default: `docker.io`) |
| `org` | `String` | The organization in the registry containing the images. (default: `k8sgerrit`) |
| `tag` | `String` | The tag/version of the images. (default: `latest`) |

## GerritClusterIngressConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether to configure an ingress provider to manage the ingress traffic in the GerritCluster (default: `false`) |
| `type` | [`IngressType`](#ingresstype) | Which type of ingress provider to use (default: `NONE`) |
| `host` | `string` | Hostname to be used by the ingress. For each Gerrit deployment a new subdomain using the name of the respective Gerrit CustomResource will be used. |
| `annotations` | `Map<String, String>` | Annotations to be set for the ingress. This allows to configure the ingress further by e.g. setting the ingress class. This will be only used for type INGRESS and ignored otherwise. (optional) |
| `tls` | [`GerritIngressTlsConfig`](#gerritingresstlsconfig) | Configuration of TLS to be used in the ingress |

## IngressType

| Value | Description|
|---|---|
| `NONE` | No ingress provider will be configured |
| `INGRESS` | An [`Ingress`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#ingress-v1-networking-k8s-io) will be provisioned |
| `ISTIO` | [ISTIO](https://istio.io/latest/) will be configured to add the GerritCluster to the ServiceMesh |

## GerritIngressTlsConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether to use TLS (default: `false`) |
| `secret` | `String` | Name of the secret containing the TLS key pair. The certificate should be a wildcard certificate allowing for all subdomains under the given host. |

## GerritTemplate

| Field | Type | Description |
|---|---|---|
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource. A name is mandatory. Labels can optionally be defined. Other fields like the namespace are ignored. |
| `spec` | [`GerritTemplateSpec`](#gerrittemplatespec) | Specification for GerritTemplate |

## GerritTemplateSpec

| Field | Type | Description |
|---|---|---|
| `tolerations` | [`Toleration`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#toleration-v1-core)-Array | Pod tolerations (optional) |
| `affinity` | [`Affinity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#affinity-v1-core) | Pod affinity (optional) |
| `topologySpreadConstraints` | [`TopologySpreadConstraint`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#topologyspreadconstraint-v1-core)-Array | Pod topology spread constraints (optional) |
| `priorityClassName` | `String` | [PriorityClass](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/) to be used with the pod (optional) |
| `replicas` | `int` | Number of pods running Gerrit in the StatefulSet (default: 1) |
| `updatePartition` | `int` | Ordinal at which to start updating pods. Pods with a lower ordinal will not be updated. (default: 0) |
| `resources` | [`ResourceRequirements`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#resourcerequirements-v1-core) | Resource requirements for the Gerrit container |
| `startupProbe` | [`GerritProbe`](#gerritprobe) | [Startup probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes). The action will be set by the operator. All other probe parameters can be set. |
| `readinessProbe` | [`GerritProbe`](#gerritprobe) | [Readiness probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes). The action will be set by the operator. All other probe parameters can be set. |
| `livenessProbe` | [`GerritProbe`](#gerritprobe) | [Liveness probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes). The action will be set by the operator. All other probe parameters can be set. |
| `gracefulStopTimeout` | `long` | Seconds the pod is allowed to shutdown until it is forcefully killed (default: 30) |
| `service` | [`GerritServiceConfig`](#gerritserviceconfig) | Configuration for the service used to manage network access to the StatefulSet |
| `site` | [`GerritSite`](#gerritsite) | Configuration concerning the Gerrit site directory |
| `plugins` | [`GerritPlugin`](#gerritplugin)-Array | List of Gerrit plugins to install. These plugins can either be packaged in the Gerrit war-file or they will be downloaded. (optional) |
| `configFiles` | `Map<String, String>` | Configuration files for Gerrit that will be mounted into the Gerrit site's etc-directory (gerrit.config is mandatory) |
| `secrets` | `Set<String>` | Names of secrets containing configuration files, e.g. secure.config, that will be mounted into the Gerrit site's etc-directory (optional) |
| `mode` | [`GerritMode`](#gerritmode) | In which mode Gerrit should be run. (default: PRIMARY) |

## GerritProbe

**Extends:** [`Probe`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#probe-v1-core)

The fields `exec`, `grpc`, `httpGet` and `tcpSocket` cannot be set manually anymore
compared to the parent object. All other options can still be configured.

## GerritServiceConfig

| Field | Type | Description |
|---|---|---|
| `type` | `String` | Service type (default: `NodePort`) |
| `httpPort` | `int` | Port used for HTTP requests (default: `80`) |
| `sshPort` | `Integer` | Port used for SSH requests (optional; if unset, SSH access is disabled). If Istio is used, the Gateway will be automatically configured to accept SSH requests. If an Ingress controller is used, SSH requests will only be served by the Service itself! |

## GerritSite

| Field | Type | Description |
|---|---|---|
| `size` | [`Quantity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#quantity-resource-core) | Size of the volume used to persist not otherwise persisted site components (e.g. git repositories are persisted in a dedicated volume) (mandatory) |

## GerritPlugin

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Name of the plugin |
| `url` | `URL` | URL of the plugin, if it should be downloaded. If the URL is not set, the plugin is expected to be packaged in the war-file (optional) |
| `sha1` | `String` | SHA1-checksum of the plugin JAR-file. (mandatory, if `url` is set) |
| `installAsLibrary` | `boolean` | Some plugins also need to be installed as a library. If set to `true` the plugin JAR will be symlinked to the `lib`-directory in the Gerrit site. (default: `false`) |

## GerritMode

| Value | Description|
|---|---|
| `PRIMARY` | A primary Gerrit |
| `REPLICA` | A Gerrit Replica, which only serves git fetch/clone requests |

## GerritSpec

**Extends:** [`GerritTemplateSpec`](#gerrittemplatespec)

| Field | Type | Description |
|---|---|---|
| `storage` | [`GerritStorageConfig`](#gerritstorageconfig) | Storage used by Gerrit instances |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |
| `ingress` | [`IngressConfig`](#ingressconfig) | Ingress configuration for Gerrit |

## GerritStatus

| Field | Type | Description |
|---|---|---|
| `ready` | `boolean` | Whether the Gerrit instance is ready |
| `appliedSecretVersions` | `Map<String, String>` | Versions of each secret currently mounted into Gerrit pods |

## IngressConfig

| Field | Type | Description |
|---|---|---|
| `type` | [`IngressType`](#ingresstype) | Which type of ingress provider is being used. |
| `host` | `string` | Hostname that is being used by the ingress provider for this Gerrit instance. |
| `tlsEnabled` | `boolean` | Whether the ingress provider enables TLS. (default: `false`) |

## ReceiverTemplate

| Field | Type | Description |
|---|---|---|
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource. A name is mandatory. Labels can optionally be defined. Other fields like the namespace are ignored. |
| `spec` | [`ReceiverTemplateSpec`](#receivertemplatespec) | Specification for ReceiverTemplate |

## ReceiverTemplateSpec

| Field | Type | Description |
|---|---|---|
| `tolerations` | [`Toleration`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#toleration-v1-core)-Array | Pod tolerations (optional) |
| `affinity` | [`Affinity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#affinity-v1-core) | Pod affinity (optional) |
| `topologySpreadConstraints` | [`TopologySpreadConstraint`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#topologyspreadconstraint-v1-core)-Array | Pod topology spread constraints (optional) |
| `priorityClassName` | `String` | [PriorityClass](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/) to be used with the pod (optional) |
| `replicas` | `int` | Number of pods running the receiver in the Deployment (default: 1) |
| `maxSurge` | `IntOrString` | Ordinal or percentage of pods that are allowed to be created in addition during rolling updates. (default: `1`) |
| `maxUnavailable` | `IntOrString` | Ordinal or percentage of pods that are allowed to be unavailable during rolling updates. (default: `1`) |
| `resources` | [`ResourceRequirements`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#resourcerequirements-v1-core) | Resource requirements for the Receiver container |
| `readinessProbe` | [`ReceiverProbe`](#receiverprobe) | [Readiness probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes). The action will be set by the operator. All other probe parameters can be set. |
| `livenessProbe` | [`ReceiverProbe`](#receiverprobe) | [Liveness probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes). The action will be set by the operator. All other probe parameters can be set. |
| `service` | [`ReceiverServiceConfig`](#receiverserviceconfig) |  Configuration for the service used to manage network access to the Deployment |
| `credentialSecretRef` | `String` | Name of the secret containing the .htpasswd file used to configure basic authentication within the Apache server (mandatory) |

## ReceiverSpec

**Extends:** [`ReceiverTemplateSpec`](#receivertemplatespec)

| Field | Type | Description |
|---|---|---|
| `storage` | [`GerritStorageConfig`](#gerritstorageconfig) | Storage used by Gerrit/Receiver instances |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |
| `ingress` | [`IngressConfig`](#ingressconfig) | Ingress configuration for Gerrit |

## ReceiverStatus

| Field | Type | Description |
|---|---|---|
| `ready` | `boolean` | Whether the Receiver instance is ready |
| `appliedCredentialSecretVersion` | `String` | Version of credential secret currently mounted into Receiver pods |

## ReceiverProbe

**Extends:** [`Probe`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#probe-v1-core)

The fields `exec`, `grpc`, `httpGet` and `tcpSocket` cannot be set manually anymore
compared to the parent object. All other options can still be configured.

## ReceiverServiceConfig

| Field | Type | Description |
|---|---|---|
| `type` | `String` | Service type (default: `NodePort`) |
| `httpPort` | `int` | Port used for HTTP requests (default: `80`) |

## GitGarbageCollectionSpec

| Field | Type | Description |
|---|---|---|
| `cluster` | `string` | Name of the Gerrit cluster this Gerrit is a part of. (mandatory) |
| `tolerations` | [`Toleration`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#toleration-v1-core)-Array | Pod tolerations (optional) |
| `affinity` | [`Affinity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#affinity-v1-core) | Pod affinity (optional) |
| `schedule` | `string` | Cron schedule defining when to run git gc (mandatory) |
| `projects` | `Set<String>` | List of projects to gc. If omitted, all projects not handled by other Git GC jobs will be gc'ed. Only one job gc'ing all projects can exist. (default: `[]`) |
| `resources` | [`ResourceRequirements`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#resourcerequirements-v1-core) | Resource requirements for the GitGarbageCollection container |

## GitGarbageCollectionStatus

| Field | Type | Description |
|---|---|---|
| `replicateAll` | `boolean` | Whether this GitGarbageCollection handles all projects |
| `excludedProjects` | `Set<String>` | List of projects that were excluded from this GitGarbageCollection, since they are handled by other Jobs |
| `state` | [`GitGcState`](#gitgcstate) | State of the GitGarbageCollection |

## GitGcState

| Value | Description|
|---|---|
| `ACTIVE` | GitGarbageCollection is scheduled |
| `INACTIVE` | GitGarbageCollection is not scheduled |
| `CONFLICT` | GitGarbageCollection conflicts with another GitGarbageCollection |
| `ERROR` | Controller failed to schedule GitGarbageCollection |
