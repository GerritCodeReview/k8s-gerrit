# Gerrit Operator - API Reference

- [Gerrit Operator - API Reference](#gerrit-operator---api-reference)
  - [General Remarks](#general-remarks)
    - [Inheritance](#inheritance)
  - [GerritCluster](#gerritcluster)
  - [Gerrit](#gerrit)
  - [Receiver](#receiver)
  - [GitGarbageCollection](#gitgarbagecollection)
  - [GerritNetwork](#gerritnetwork)
  - [IncomingReplicationTask](#incomingreplicationtask)
  - [GerritIndexer](#gerritindexer)
  - [GerritClusterSpec](#gerritclusterspec)
  - [GerritClusterStatus](#gerritclusterstatus)
  - [StorageConfig](#storageconfig)
  - [GerritStorageConfig](#gerritstorageconfig)
  - [StorageClassConfig](#storageclassconfig)
  - [NfsWorkaroundConfig](#nfsworkaroundconfig)
  - [SharedStorage](#sharedstorage)
  - [PluginCacheConfig](#plugincacheconfig)
  - [ExternalPVCConfig](#externalpvcconfig)
  - [ContainerImageConfig](#containerimageconfig)
  - [BusyBoxImage](#busyboximage)
  - [GerritRepositoryConfig](#gerritrepositoryconfig)
  - [GerritClusterIngressConfig](#gerritclusteringressconfig)
  - [GerritIngressTlsConfig](#gerritingresstlsconfig)
  - [GerritIngressAmbassadorConfig](#gerritingressambassadorconfig)
  - [GerritIstioConfig](#gerritistioconfig)
  - [GlobalRefDbConfig](#globalrefdbconfig)
  - [RefDatabase](#refdatabase)
  - [SpannerRefDbConfig](#spannerrefdbconfig)
  - [ZookeeperRefDbConfig](#zookeeperrefdbconfig)
  - [IndexConfig](#indexconfig)
  - [IndexType](#indextype)
  - [ElasticSearchConfig](#elasticsearchconfig)
  - [EventsBrokerConfig](#eventsbrokerconfig)
  - [BrokerType](#brokertype)
  - [KafkaConfig](#kafkaconfig)
  - [FluentBitSidecarConfig](#fluentbitsidecarconfig)
  - [GerritTemplate](#gerrittemplate)
  - [GerritTemplateSpec](#gerrittemplatespec)
  - [GerritProbe](#gerritprobe)
  - [GerritServiceConfig](#gerritserviceconfig)
  - [GerritSite](#gerritsite)
  - [GerritModule](#gerritmodule)
  - [GerritModuleData](#gerritmoduledata)
  - [GerritPlugin](#gerritplugin)
  - [GerritMode](#gerritmode)
  - [GerritDebugConfig](#gerritdebugconfig)
  - [GerritSpec](#gerritspec)
  - [GerritStatus](#gerritstatus)
  - [IngressConfig](#ingressconfig)
  - [ReceiverTemplate](#receivertemplate)
  - [ReceiverTemplateSpec](#receivertemplatespec)
  - [ReceiverSpec](#receiverspec)
  - [ReceiverStatus](#receiverstatus)
  - [ReceiverProbe](#receiverprobe)
  - [ReceiverServiceConfig](#receiverserviceconfig)
  - [GitGarbageCollectionSpec](#gitgarbagecollectionspec)
  - [GitGarbageCollectionStatus](#gitgarbagecollectionstatus)
  - [GitGcState](#gitgcstate)
  - [GerritNetworkSpec](#gerritnetworkspec)
  - [NetworkMember](#networkmember)
  - [NetworkMemberWithSsh](#networkmemberwithssh)
  - [ScheduledTasks](#scheduledtasks)
  - [IncomingReplicationTaskTemplate](#incomingreplicationtasktemplate)
  - [IncomingReplicationTaskTemplateSpec](#incomingreplicationtasktemplatespec)
  - [IncomingReplicationConfig](#incomingreplicationconfig)
  - [Remote](#remote)
  - [Fetch](#fetch)
  - [IncomingReplicationTaskSpec](#incomingreplicationtaskspec)
  - [GerritIndexerSpec](#gerritindexerspec)
  - [GerritIndexerStorage](#gerritindexerstorage)
  - [GerritIndexerVolumeRef](#gerritindexervolumeref)

## General Remarks

### Inheritance

Some objects inherit the fields of other objects. In this case the section will
contain an **Extends:** label to link to the parent object, but it will not repeat
inherited fields.

## GerritCluster

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
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
apiVersion: "gerritoperator.google.com/v1beta13"
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

    sharedStorage:
      externalPVC:
        enabled: false
        claimName: ""
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    pluginCache:
      enabled: false

  ingress:
    enabled: true
    host: example.com
    annotations: {}
    tls:
      enabled: false
      secret: ""
    ambassador:
      id: []
      createHost: false
    istio:
      gatewaySelector:
        istio: ingressgateway

  refdb:
    database: NONE
    spanner:
      projectName: ""
      instance: ""
      database: ""
    zookeeper:
      connectString: ""
      rootNode: ""

  index:
    type: LUCENE
    elasticsearch:
      server: es.example.com
      config: |-
        [elasticsearch]
          codec = default

  fluentBitSidecar:
    enabled: true
    image: fluent/fluent-bit:latest
    config: |-
      [OUTPUT]
        Name              stdout
        Match             *
      [FILTER]
        Name              modify
        Match             *
        Add k8s.pod.name  ${POD_NAME}

  serverId: ""

  gerrits:
  - metadata:
      name: gerrit
      labels:
        app: gerrit
    spec:
      serviceAccount: gerrit

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

      debug:
        enabled: false
        suspend: false

      site:
        size: 1Gi

      plugins:
      # Installs a packaged plugin
      - name: delete-project

      # Plugin with data files
      - name: hooks
        data:
          secretRef: hooks
          executable: false

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

      libs:
      - name: global-refdb
        url: https://example.com/global-refdb.jar
        sha1: 3d533a536b0d4e0184f824478c24bc8dfe896d06

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

      envVars:
      - name: GOOGLE_APPLICATION_CREDENTIALS
        value: /var/gerrit/etc/gcp-credentials.json

      secretRef: gerrit-secure-config

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

  scheduledTasks:
    incomingReplication:
    - metadata:
        name: github
      spec:
        schedule: "0 * * * *"
        config:
          remotes:
          - name: github
            url: https://github.com
            fetch:
            - remoteRepo: fabric8io/kubernetes-client
            - remoteRepo: operator-framework/java-operator-sdk
              localRepo: josdk
              refSpec: "+refs/heads/master:refs/heads/github/master"
        secretRef: github-repl-secret
```

## Gerrit

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
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
apiVersion: "gerritoperator.google.com/v1beta13"
kind: Gerrit
metadata:
  name: gerrit
spec:
  serviceAccount: gerrit

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

  debug:
    enabled: false
    suspend: false

  site:
    size: 1Gi

  plugins:
  # Installs a plugin packaged into the gerrit.war file
  - name: delete-project

  # Plugin with data files
  - name: hooks
    data:
      secretRef: hooks
      executable: false

  # Downloads and installs a plugin
  - name: javamelody
    url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-javamelody-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar
    sha1: 40ffcd00263171e373a24eb6a311791b2924707c

  # If the `installAsLibrary` option is set to `true` the plugin jar-file will
  # be symlinked to the lib directory and thus installed as a library as well.
  - name: saml
    url: https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-saml-bazel-master-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/saml/saml.jar
    sha1: 6dfe8292d46b179638586e6acf671206f4e0a88b
    installAsLibrary: true
    data:
      secretRef: sp-metadata.xml

  libs:
  - name: global-refdb
    url: https://example.com/global-refdb.jar
    sha1: 3d533a536b0d4e0184f824478c24bc8dfe896d06

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

  envVars:
  - name: GOOGLE_APPLICATION_CREDENTIALS
    value: /var/gerrit/etc/gcp-credentials.json

  secretRef: gerrit-secure-config

  serverId: ""

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

    sharedStorage:
      externalPVC:
        enabled: false
        claimName: ""
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

    pluginCache:
      enabled: false

  ingress:
    host: example.com
    tlsEnabled: false

  refdb:
    database: NONE
    spanner:
      projectName: ""
      instance: ""
      database: ""
    zookeeper:
      connectString: ""
      rootNode: ""

  index:
    type: LUCENE
    elasticsearch:
      server: es.example.com
      config: |-
        [elasticsearch]
          codec = default
```

## Receiver

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
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
apiVersion: "gerritoperator.google.com/v1beta13"
kind: Receiver
metadata:
  name: receiver
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

    sharedStorage:
      externalPVC:
        enabled: false
        claimName: ""
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1

  ingress:
    host: example.com
    tlsEnabled: false
```

## GitGarbageCollection

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
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
apiVersion: "gerritoperator.google.com/v1beta13"
kind: GitGarbageCollection
metadata:
  name: gitgc
spec:
  cluster: gerrit
  schedule: "*/5 * * * *"

  projects: []

  disableBitmapIndex: false
  disablePackRefs: false
  preservePacks: false

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

## GerritNetwork

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
**Kind**: GerritNetwork

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`GerritNetworkSpec`](#gerritnetworkspec) | Specification for GerritNetwork |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1beta13"
kind: GerritNetwork
metadata:
  name: gerrit-network
spec:
  ingress:
    enabled: true
    host: example.com
    annotations: {}
    tls:
      enabled: false
      secret: ""
    istio:
      gatewaySelector:
        istio: ingressgateway
  receiver:
    name: receiver
    httpPort: 80
  primaryGerrit: {}
    # name: gerrit-primary
    # httpPort: 80
    # httpPort: 29418
  gerritReplica:
    name: gerrit
    httpPort: 80
    httpPort: 29418
```

## IncomingReplicationTask

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
**Kind**: IncomingReplicationTask

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`IncomingReplicationTaskSpec`](#incomingreplicationtaskspec) | Specification for IncomingReplicationTask |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1beta13"
kind: IncomingReplicationTask
metadata:
  name: incoming-repl-task
spec:
  config:
    remotes:
    - fetch:
      - remoteRepo: fabric8io/kubernetes-client
      name: github
      timeout: 5m
      url: https://github.com
  schedule: '*/5 * * * *'
  secretRef: incoming-repl-test

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

    sharedStorage:
      externalPVC:
        enabled: false
        claimName: ""
      size: 1Gi
      volumeName: ""
      selector:
        matchLabels:
          volume-type: ssd
          aws-availability-zone: us-east-1
```

## GerritIndexer

---

**Group**: gerritoperator.google.com \
**Version**: v1beta13 \
**Kind**: GerritIndexer

---


| Field | Type | Description |
|---|---|---|
| `apiVersion` | `String` | APIVersion of this resource |
| `kind` | `String` | Kind of this resource |
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource |
| `spec` | [`GerritIndexerSpec`](#gerritindexerspec) | Specification for GerritIndexer |

Example:

```yaml
apiVersion: "gerritoperator.google.com/v1beta13"
kind: GerritIndexer
metadata:
  name: gerrit-indexer
  namespace: gerrit
spec:
  cluster: gerrit-cluster
  index:
    type: LUCENE
    elasticsearch:
      server: es.example.com
      config: |-
        [elasticsearch]
          codec = default
  resources:
    requests:
      cpu: 2
      memory: 5Gi
    limits:
      cpu: 3
      memory: 6Gi
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
  storage:
    site:
      persistentVolumeClaim: gerrit-site-gerrit-0-snapshot
    repositories:
      persistentVolumeClaim: shared-pvc
      subPath: git
    output:
      persistentVolumeClaim: shared-pvc
      subPath: shared/indexes
```


## GerritClusterSpec

| Field | Type | Description |
|---|---|---|
| `storage` | [`GerritStorageConfig`](#gerritstorageconfig) | Storage used by Gerrit instances |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |
| `ingress` | [`GerritClusterIngressConfig`](#gerritclusteringressconfig) | Ingress traffic handling in GerritCluster |
| `refdb` | [`GlobalRefDbConfig`](#globalrefdbconfig) | The Global RefDB used by Gerrit |
| `index` | [`IndexConfig`](#indexconfig) | The search index used by Gerrit |
| `fluentBitSidecar` | [`FluentBitSidecarConfig`](#fluentbitsidecarconfig) | The Fluent Bit sidecar for application logging |
| `serverId` | `String` | The serverId to be used for all Gerrit instances (default: `<namespace>/<name>`) |
| `gerrits` | [`GerritTemplate`](#gerrittemplate)-Array | A list of Gerrit instances to be installed in the GerritCluster. Only a single primary Gerrit and a single Gerrit Replica is permitted. |
| `receiver` | [`ReceiverTemplate`](#receivertemplate) | A Receiver instance to be installed in the GerritCluster. |
| `scheduledTasks` | [`ScheduledTasks`](#scheduledtasks) | Scheduled tasks to install into the GerritCluster |

## GerritClusterStatus

| Field | Type | Description |
|---|---|---|
| `members` | `Map<String, List<String>>` | A map listing all Gerrit and Receiver instances managed by the GerritCluster by name |

## StorageConfig

| Field | Type | Description |
|---|---|---|
| `storageClasses` | [`StorageClassConfig`](#storageclassconfig) | StorageClasses used in the GerritCluster |
| `sharedStorage` | [`SharedStorage`](#sharedstorage) | Volume used for resources shared between Gerrit instances except git repositories |

## GerritStorageConfig

Extends [StorageConfig](#StorageConfig).

| Field | Type | Description |
|---|---|---|
| `pluginCache` | [`PluginCacheConfig`](#plugincacheconfig) | Configuration of cache for downloaded plugins |

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
| `externalPVC` | [`ExternalPVCConfig`](#externalpvcconfig) | Configuration regarding the use of an external / manually created PVC |
| `size` | [`Quantity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#quantity-resource-core) | Size of the volume (mandatory) |
| `volumeName` | `String` | Name of a specific persistent volume to claim (optional) |
| `selector` | [`LabelSelector`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#labelselector-v1-meta) | Selector to select a specific persistent volume (optional) |

## PluginCacheConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | If enabled, downloaded plugins will be cached. (default: `false`) |

## ExternalPVCConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | If enabled, a provided PVC will be used instead of creating one. (default: `false`) |
| `claimName` | `String` | Name of the PVC to be used. |

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
| `host` | `string` | Hostname to be used by the ingress. For each Gerrit deployment a new subdomain using the name of the respective Gerrit CustomResource will be used. |
| `annotations` | `Map<String, String>` | Annotations to be set for the ingress. This allows to configure the ingress further by e.g. setting the ingress class. This will be only used for type INGRESS and ignored otherwise. (optional) |
| `tls` | [`GerritIngressTlsConfig`](#gerritingresstlsconfig) | Configuration of TLS to be used in the ingress |
| `ambassador` | [`GerritIngressAmbassadorConfig`](#gerritingressambassadorconfig) | Ambassador configuration. Only relevant when the INGRESS environment variable is set to "ambassador" in the operator |
| `istio` | [`GerritIstioConfig`](#gerritistioconfig) | Istio configuration. Only relevant when the INGRESS environment variable is set to "istio" in the operator |

## GerritIngressTlsConfig

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether to use TLS (default: `false`) |
| `secret` | `String` | Name of the secret containing the TLS key pair. The certificate should be a wildcard certificate allowing for all subdomains under the given host. |

## GerritIngressAmbassadorConfig

| Field | Type | Description |
|---|---|---|
| `id` | `List<String>` | The operator uses the ids specified in `ambassadorId` to set the [ambassador_id](https://www.getambassador.io/docs/edge-stack/1.14/topics/running/running#ambassador_id) spec field in the Ambassador CustomResources it creates (`Mapping`, `TLSContext`). (optional) |
| `createHost`| `boolean` | Specify whether you want the operator to create a `Host` resource. This will be required if you don't have a wildcard host set up in your cluster. Default is `false`. (optional) |

## GerritIstioConfig

| Field | Type | Description |
|---|---|---|
| `gatewaySelector` | `Map<String, String>` | Labels used to select the Istio Ingressgateway Deployment to use (default: `istio: ingressgateway`) |

## GlobalRefDbConfig

Note, that the operator will not deploy or operate the database used for the
global refdb. It will only configure Gerrit to use it.

| Field | Type | Description |
|---|---|---|
| `database` | [`RefDatabase`](#refdatabase) | Which database to use for the global refdb. Choices: `NONE`, `SPANNER`, `ZOOKEEPER`. (default: `NONE`) |
| `spanner` | [`SpannerRefDbConfig`](#spannerrefdbconfig) | Configuration of spanner. Only used if spanner was configured to be used for the global refdb. |
| `zookeeper` | [`ZookeeperRefDbConfig`](#zookeeperrefdbconfig) | Configuration of zookeeper. Only used, if zookeeper was configured to be used for the global refdb. |

## RefDatabase

| Value | Description|
|---|---|
| `NONE` | No global refdb will be used. Not allowed, if a primary Gerrit with 2 or more instances will be installed. |
| `SPANNER` | Spanner will be used as a global refdb |
| `ZOOKEEPER` | Zookeeper will be used as a global refdb |

## SpannerRefDbConfig

Note that the spanner ref-db plugin requires google credentials to be mounted to /var/gerrit/etc/gcp-credentials.json. Instructions for generating those credentials can be found [here](https://developers.google.com/workspace/guides/create-credentials) and may be provided in the optional secretRef in [`GerritTemplateSpec`](#gerrittemplatespec).

| Field | Type | Description |
|---|---|---|
| `projectName` | `String` | Spanner project name to be used |
| `instance` | `String` | Spanner instance name to be used |
| `database` | `String` | Spanner database name to be used |

## ZookeeperRefDbConfig

| Field | Type | Description |
|---|---|---|
| `connectString` | `String` | Hostname and port of the zookeeper instance to be used, e.g. `zookeeper.example.com:2181` |
| `rootNode` | `String` | Root node that will be used to store the global refdb data. Will be set automatically, if `GerritCluster` is being used. |

## IndexConfig

Note, that the operator will not deploy or operate the search index. It will only
configure Gerrit to use it.

| Field | Type | Description |
|---|---|---|
| `type` | [`IndexType`](#indextype) | Which index type to use. Choices: `LUCENE`, `ELASTICSEARCH`. (default: `LUCENE`) |
| `elasticsearch` | [`ElasticSearchConfig`](#elasticsearchconfig) | Configuration of elasticsearch. Only used if elasticsearch was configured to be used for the search index. |

## IndexType

| Value | Description|
|---|---|
| `LUCENE` | Lucene will be used for the search index |
| `ELASTICSEARCH` | Elasticsearch will be used for the search index (Requires Elasticsearch instance to be available.) |

## ElasticSearchConfig

| Field | Type | Description |
|---|---|---|
| `server` | `String` | URL to use for connecting to Elasticsearch |
| `config` | `String` | Config snippet used for the `elasticsearch` section in `gerrit.config` |

## EventsBrokerConfig

Note, that the operator will not deploy or operate the broker used for the
events broker. It will only configure Gerrit to use it.

| Field         | Type                                            | Description                                                                                         |
|---------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `brokerType`  | [`BrokerType`](#brokertype)                     | Which broker to use for events broker. Choices: `NONE`, `KAFKA`. (default: `NONE`)                  |
| `kafkaConfig` | [`KafkaConfig`](#kafkaconfig)                   | Configuration of Kafka. Only used if Kafka was configured to be used for the events broker.         |

## BrokerType

| Value   | Description                         |
|---------|-------------------------------------|
| `NONE`  | No broker will be used.             |
| `KAFKA` | Kafka will be used as events broker |

## KafkaConfig

| Field           | Type     | Description                                                          |
|-----------------|----------|----------------------------------------------------------------------|
| `connectString` | `String` | Hostname and port of Kafka to be used, e.g. `kafka.example.com:9092` |

## FluentBitSidecarConfig

Fluent Bit is installed as a sidecar container to each Gerrit pod, which allows application
logs to be collected.

The default configuration is to label logs with the file name, pod name, and then output
to stdout. Any custom configuration will override the output to stdout and pod name label.
The input and file name label is always configured by the operator.

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether or not to create the sidecar (default: `false`) |
| `image` | `String` | Fluent Bit image from docker (default: `fluent/fluent-bit:latest`) |
| `config` | `String` | Additional config for fluent bit. [Available options](https://docs.fluentbit.io/manual/administration/configuring-fluent-bit/classic-mode/configuration-file) |

## GerritTemplate

| Field | Type | Description |
|---|---|---|
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource. A name is mandatory. Labels can optionally be defined. Other fields like the namespace are ignored. |
| `spec` | [`GerritTemplateSpec`](#gerrittemplatespec) | Specification for GerritTemplate |

## GerritTemplateSpec

| Field | Type | Description |
|---|---|---|
| `serviceAccount` | `String` | ServiceAccount to be used by Gerrit. Required for service discovery when using the high-availability plugin |
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
| `libs` | [`GerritModule`](#gerritmodule)-Array | List of Gerrit library modules to install. These lib modules will be downloaded. (optional) |
| `configFiles` | `Map<String, String>` | Configuration files for Gerrit that will be mounted into the Gerrit site's etc-directory (gerrit.config is mandatory) |
| `envVars` |  [`List<EnvVar>`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.23/#envvar-v1-core) | Environment variables that will be set in the pod. (optional) |
| `secretRef` | `String` | Name of secret containing configuration files, e.g. secure.config, that will be mounted into the Gerrit site's etc-directory (optional) |
| `mode` | [`GerritMode`](#gerritmode) | In which mode Gerrit should be run. (default: PRIMARY) |
| `debug` | [`GerritDebugConfig`](#gerritdebugconfig) | Enable the debug-mode for Gerrit |

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

## GerritModule

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Name of the module/plugin |
| `url` | `String` | URL of the module/plugin, if it should be downloaded. If the URL is not set, the plugin is expected to be packaged in the war-file (not possible for lib-modules). (optional) |
| `sha1` | `String` | SHA1-checksum of the module/plugin JAR-file. (mandatory, if `url` is set) |
| `data` | [`GerritModuleData`](#gerritmoduledata) | Plugin data mounted under the gerrit site's `data/$name` directory |

## GerritModuleData

| Field | Type | Description |
|---|---|---|
| `secretRef` | `String` | Name of a secretRef. The secret will be mounted under the gerrit site's `data/$module_name` directory|
| `executable` | `boolean` | Whether the mounted data files should have executable permissions (file mode 754 instead of 644). (default: false) |

## GerritPlugin

**Extends:** [`GerritModule`](#gerritmodule)

| Field | Type | Description |
|---|---|---|
| `installAsLibrary` | `boolean` | Some plugins also need to be installed as a library. If set to `true` the plugin JAR will be symlinked to the `lib`-directory in the Gerrit site. (default: `false`) |

## GerritMode

| Value | Description|
|---|---|
| `PRIMARY` | A primary Gerrit |
| `REPLICA` | A Gerrit Replica, which only serves git fetch/clone requests |

## GerritDebugConfig

These options allow to debug Gerrit. It will enable debugging in all pods and
expose the port 8000 in the container. Port-forwarding is required to connect the
debugger.
Note, that all pods will be restarted to enable the debugger. Also, if `suspend`
is enabled, ensure that the lifecycle probes are configured accordingly to prevent
pod restarts before Gerrit is ready.

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Whether to enable debugging. (default: `false`) |
| `suspend` | `boolean` | Whether to suspend Gerrit on startup. (default: `false`) |

## GerritSpec

**Extends:** [`GerritTemplateSpec`](#gerrittemplatespec)

| Field | Type | Description |
|---|---|---|
| `storage` | [`GerritStorageConfig`](#gerritstorageconfig) | Storage used by Gerrit instances |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |
| `ingress` | [`IngressConfig`](#ingressconfig) | Ingress configuration for Gerrit |
| `refdb` | [`GlobalRefDbConfig`](#globalrefdbconfig) | The Global RefDB used by Gerrit |
| `fluentBitSidecar` | [`FluentBitSidecarConfig`](#fluentbitsidecarconfig) | The Fluent Bit sidecar for application logging |
| `serverId` | `String` | The serverId to be used for all Gerrit instances |

## GerritStatus

| Field | Type | Description |
|---|---|---|
| `ready` | `boolean` | Whether the Gerrit instance is ready |
| `appliedConfigMapVersions` | `Map<String, String>` | Versions of each ConfigMap currently mounted into Gerrit pods |
| `appliedSecretVersions` | `Map<String, String>` | Versions of each secret currently mounted into Gerrit pods |

## IngressConfig

| Field | Type | Description |
|---|---|---|
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
| `storage` | [`StorageConfig`](#storageconfig) | Storage used by Gerrit/Receiver instances |
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
| `disableBitmapIndex` | `Boolean` | Whether to disable bitmap index creation. This will increase push performance but decrease performance when serving fetches (default: `false`) |
| `disablePackRefs` | `Boolean` | Whether to disable packing refs. Packing refs might interfere with pushes due to locking packed-refs (default: `false`) |
| `preservePacks` | `Boolean` | Whether to preserve existing packs. Requires additional storage, but prevents failing fetches due to deleted packs (default: `false`) |
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

## GerritNetworkSpec

| Field | Type | Description |
|---|---|---|
| `ingress` | [`GerritClusterIngressConfig`](#gerritclusteringressconfig) | Ingress traffic handling in GerritCluster |
| `receiver` | [`NetworkMember`](#networkmember) | Receiver in the network. |
| `primaryGerrit` | [`NetworkMemberWithSsh`](#networkmemberwithssh) | Primary Gerrit in the network. |
| `gerritReplica` | [`NetworkMemberWithSsh`](#networkmemberwithssh) | Gerrit Replica in the network. |

## NetworkMember

| Field      | Type     | Description                |
|------------|----------|----------------------------|
| `name`     | `String` | Name of the network member |
| `httpPort` | `int`    | Port used for HTTP(S)      |

## NetworkMemberWithSsh

**Extends:** [`NetworkMember`](#networkmember)

| Field     | Type  | Description       |
|-----------|-------|-------------------|
| `sshPort` | `int` | Port used for SSH |

## ScheduledTasks

| Field | Type | Description |
|---|---|---|
| `incomingReplication` | [`IncomingReplicationTaskTemplate`](#incomingreplicationtasktemplate) | Scheduled task to fetch from remote git servers |

## IncomingReplicationTaskTemplate

| Field | Type | Description |
|---|---|---|
| `metadata` | [`ObjectMeta`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#objectmeta-v1-meta) | Metadata of the resource. A name is mandatory. Labels can optionally be defined. Other fields like the namespace are ignored. |
| `spec` | [`IncomingReplicationTaskTemplateSpec`](#incomingreplicationtasktemplatespec) | Specification for IncomingReplicationTaskTemplate |

## IncomingReplicationTaskTemplateSpec

| Field | Type | Description |
|---|---|---|
| `schedule` | `String` | Schedule in which to execute the job |
| `config` | [`IncomingReplicationConfig`](#incomingreplicationconfig) | Configuration of replication task |
| `resources` | [`ResourceRequirements`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#resourcerequirements-v1-core) | Resource requirements for the GitGarbageCollection container |
| `tolerations` | [`Toleration`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#toleration-v1-core)-Array | Pod tolerations (optional) |
| `affinity` | [`Affinity`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#affinity-v1-core) | Pod affinity (optional) |
| `secretRef` | `String` | Name of the secret containing the .netrc file containing credentials for all remote git servers |

## IncomingReplicationConfig

| Field | Type | Description |
|---|---|---|
| `remotes` | [`List<Remote>`](#remote) | List of remotes from which to fetch |

## Remote

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Name of the remote (mandatory) |
| `url` | `String` | Base URL of the remote, e.g. `https://gerrit-review.googlesource.com/a` (mandatory) |
| `timeout` | `String` | Timeout for the fetch (default: `5m`) |
| `fetch` | [`List<Fetch>`](#fetch) | List of projects to fetch |

## Fetch

| Field | Type | Description |
|---|---|---|
| `remoteRepo` | `String` | Name of the remote repository (mandatory) |
| `localRepo` | `String` | Name of the local repository (default: same as `remoteRepo`) |
| `refSpec` | `String` | RefSpec to fetch (default: +refs/heads/*:refs/heads/{{remote.name}}/*) |

## IncomingReplicationTaskSpec

**Extends:** [`IncomingReplicationTaskTemplateSpec`](#incomingreplicationtasktemplatespec)

| Field | Type | Description |
|---|---|---|
| `storage` | [`StorageConfig`](#storageconfig) | Storage used by GerritCluster |
| `containerImages` | [`ContainerImageConfig`](#containerimageconfig) | Container images used inside GerritCluster |

## GerritIndexerSpec

| Field | Type | Description |
|---|---|---|
| `cluster` | `String` | Name of the GerritCluster serving the repositories to be indexed (mandatory) |
| `index` | `IndexConfig` | Overrides the `IndexConfig` in the `GerritCluster`. Meant for migration scenarios. (default: `null`, i.e. `IndexConfig` from `GerritCluster` will be used) |
| `resources` | [`ResourceRequirements`](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#resourcerequirements-v1-core) | Resource requirements for the GerritIndexer container |
| `configFiles` | `Map<String, String>` | Configuration files to be mounted in to `$SITE/etc` |
| `storage` | [`GerritIndexerStorage`](#gerritindexerstorage) | Storage configuration of the GerritIndexer job |

## GerritIndexerStorage

| Field | Type | Description |
|---|---|---|
| `site` | [`GerritIndexerVolumeRef`](#gerritindexervolumeref) | Volume containing the site of a primary Gerrit in the GerritCluster (mandatory) |
| `repositories` | [`GerritIndexerVolumeRef`](#gerritindexervolumeref) | Volume containing the repositories served by the GerritCluster (mandatory) |
| `output` | [`GerritIndexerVolumeRef`](#gerritindexervolumeref) | Volume used to store the resulting indexes. If not provided, the volume containing the repositories and subdirectory `shared/indexes` will be used. |

## GerritIndexerVolumeRef

| Field | Type | Description |
|---|---|---|
| `persistentVolumeClaim` | `String` | Name of the PersistentVolumeClaim that should be mounted (mandatory) |
| `subPath` | `String` | Path in the volume to mount for the respective volume. (defaults: site: `null`; repositories: `git`; output: `shared/indexes`) |
