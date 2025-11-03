# Gerrit Operator

- [Gerrit Operator](#gerrit-operator)
  - [Development](#development)
  - [Prerequisites](#prerequisites)
    - [Shared Storage (ReadWriteMany)](#shared-storage-readwritemany)
    - [Ingress provider](#ingress-provider)
  - [Deploy](#deploy)
    - [Using helm charts](#using-helm-charts)
      - [gerrit-operator-crds](#gerrit-operator-crds)
      - [gerrit-operator](#gerrit-operator-1)
    - [Without the helm charts](#without-the-helm-charts)
    - [Updating](#updating)
  - [CustomResources](#customresources)
    - [GerritCluster](#gerritcluster)
    - [Gerrit](#gerrit)
    - [GitGarbageCollection](#gitgarbagecollection)
    - [Receiver](#receiver)
    - [GerritNetwork](#gerritnetwork)
    - [IncomingReplicationTask](#incomingreplicationtask)
  - [Configuration of Gerrit](#configuration-of-gerrit)
  - [Feature toggles](#feature-toggles)
    - [Cluster Mode](#cluster-mode)
      - [With helm charts](#with-helm-charts)
      - [Without helm charts](#without-helm-charts)
  - [Minikube](#minikube)
    - [Prerequisites](#prerequisites-1)
    - [Starting Minikube](#starting-minikube)
    - [Build images](#build-images)
    - [Gerrit Operator](#gerrit-operator-2)
    - [NFS](#nfs)
    - [Primary Gerrit](#primary-gerrit)
    - [Adding a Gerrit Replica](#adding-a-gerrit-replica)
    - [Istio](#istio)
    - [High Availability](#high-availability)
  - [Minikube multisite](#minikube-multisite)
    - [Prerequisites](#prerequisites-2)
    - [Build images](#build-images-1)
    - [Istio](#istio-1)
    - [Gerrit Operator](#gerrit-operator-3)
    - [Deploy multisite with three replicas](#deploy-multisite-with-three-replicas)
  - [Multisite notes](#multisite-notes)
    - [Istio](#istio-2)
    - [Kafka](#kafka)
    - [Zookeeper](#zookeeper)
    - [Sync Gerrit sites](#sync-gerrit-sites)
  - [Multisite monitoring](#multisite-monitoring)
    - [Prerequisites](#prerequisites-3)
    - [Access to Prometheus](#access-to-prometheus)
    - [Access to Grafana](#access-to-grafana)
    - [Access to AlertManager](#access-to-alertmanager)

## Development

Development processes are documented [here](./operator-dev.md).

## Prerequisites

Deploying Gerrit using the operator requires some additional prerequisites to be
fulfilled:

### Shared Storage (ReadWriteMany)

Gerrit instances share the repositories and other data using shared volumes. Thus,
a StorageClass and a suitable provisioner have to be available in the cluster.
An example for such a provisioner would be the
[NFS-subdir-external-provisioner](https://github.com/kubernetes-sigs/nfs-subdir-external-provisioner).

### Ingress provider

The Gerrit Operator will also set up network routing rules and an ingress point
for the Gerrit instances it manages. The network routing rules ensure that requests
will be routed to the intended GerritCluster component, e.g. in case a primary
Gerrit and a Gerrit Replica exist in the cluster, git fetch/clone requests will
be sent to the Gerrit Replica and all other requests to the primary Gerrit.

You may specify the ingress provider by setting the `INGRESS` environment
variable in the operator Deployment manifest. That is, the choice of an ingress
provider is an operator-level setting. However, you may specify some ingress
configuration options (host, tls, etc) at the `GerritCluster` level, via
[GerritClusterIngressConfig](operator-api-reference.md#gerritclusteringressconfig).

The Gerrit Operator currently supports the following Ingress providers:

- **NONE**

  The operator will install no Ingress components. Services will still be available.
  No prerequisites are required for this case.

  If `spec.ingress.enabled` is set to `true` in GerritCluster, the operator will
  still configure network related options like `http.listenUrl` in Gerrit based on
  the other options in `spec.ingress`.

- **INGRESS**

  The operator will install an Ingress. Currently only the
  [Nginx-Ingress-Controller](https://docs.nginx.com/nginx-ingress-controller/) is
  supported, which will have to be installed in the cluster and has to be configured
  to [allow snippet configurations](https://docs.nginx.com/nginx-ingress-controller/configuration/ingress-resources/advanced-configuration-with-snippets/).
  An example of a working deployment can be found [here](../supplements/test-cluster/ingress/).

  SSH support is not fully managed by the operator, since it has to be enabled and
  [configured in the nginx ingress controller itself](https://kubernetes.github.io/ingress-nginx/user-guide/exposing-tcp-udp-services/).

- **ISTIO**

  The operator supports the use of [Istio](https://istio.io/) as a service mesh.
  An example on how to set up Istio can be found [here](../istio/gerrit.profile.yaml).

- **AMBASSADOR**

  The operator also supports [Ambassador](https://www.getambassador.io/) for
  setting up ingress to the Gerrits deployed by the operator. If you use
  Ambassador's "Edge Stack" or "Emissary Ingress" to provide ingress to your k8s
  services, you should set INGRESS=AMBASSADOR. Currently, SSH is not directly
  supported when using INGRESS=AMBASSADOR.


## Deploy
You will need to have admin privileges for your k8s cluster in order to be able
to deploy the following resources.

You may choose to deploy the operator resources using helm, or directly via
`kubectl apply`.

### Using helm charts
Make sure you have [helm](https://helm.sh/) installed in your environment.

There are two relevant helm charts.

#### gerrit-operator-crds

This chart installs the CRDs (k8s API extensions) to your k8s cluster. No chart
values need to be modified. The build initiated by the `mvn install` command
from the [Publish](#publish) section includes a step that updates the CRDs in
this helm chart to reflect any changes made to them in the operator source code.
The CRDs installed are: GerritCluster, Gerrit, GitGarbageCollection, Receiver.

You do not need to manually `helm install` this chart; this chart is installed
as a dependency of the second `gerrit-operator` helm chart as described in the
next subheading.

#### gerrit-operator

This chart installs the `gerrit-operator-crds` chart as a dependency, and the
following k8s resources:
- Deployment
- ServiceAccount
- ClusterRole
- ClusterRoleBinding

The operator itself creates a Service resource and a
ValidationWebhookConfigurations resource behind the scenes.

You will need to modify the values in `helm-charts/gerrit-operator/values.yaml`
to point the chart to the registry/org that is hosting the Docker container
image for the operator (from the [Publish](#publish) step earlier). Now,

run:
```sh
# Create a namespace for the gerrit-operator
kubectl create ns gerrit-operator

# Build the gerrit-operator-crds chart and store it in the charts/ subdirectory
helm dependency build helm-charts/gerrit-operator/

# Install the gerrit-operator-crds chart and the gerrit-operator chart
helm -n gerrit-operator install gerrit-operator helm-charts/gerrit-operator/
```

The chart itself, and all the bundled namespaced resources, are installed in the
`gerrit-operator` namespace, as per the `-n` option in the helm command.

### Without the helm charts

First all CustomResourceDefinitions have to be deployed:

```sh
kubectl apply -f operator/target/classes/META-INF/fabric8/*-v1.yml
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
kubectl apply -f operator/k8s/rbac.yaml
kubectl apply -f operator/k8s/operator.yaml
```

`k8s/operator.yaml` contains a basic deployment of the operator. Resources,
docker image name etc. might have to be adapted. For example, the ingress
provider has to be configured by setting the `INGRESS` environment variable
in `operator/k8s/operator.yaml` to either `NONE`, `INGRESS`, `ISTIO`, or
`AMBASSADOR`.

### Updating

The Gerrit Operator helm chart can be updated by running:

```sh
# Rebuild the gerrit-operator-crds chart and store it in the charts/ subdirectory
helm dependency build helm-charts/gerrit-operator/

# Install the gerrit-operator-crds chart and the gerrit-operator chart
helm -n gerrit-operator upgrade gerrit-operator helm-charts/gerrit-operator/
```

The Gerrit Operator will automatically reconcile all CustomResources in the cluster
on startup.

The GerritOperator will always only support two versions of the CRDs. The newer
version will always be the one that will be stored in ETCD. Conversion will happen
automatically during the update. Note, that this means that updates over multiple
versions will not work, but updates that include CRD version updates have to be
done in sequence.

## CustomResources

The operator manages several CustomResources that are described in more detail
below.

The API reference for all CustomResources can be found [here](operator-api-reference.md).

### GerritCluster

The GerritCluster CustomResource installs one or multiple Gerrit instances. The
operator takes over managing the state of all Gerrit instances within the cluster
and ensures that the state stays in sync. To this end it manages additional
resources that are shared between Gerrit instances or are required to synchronize
the state between Gerrit instances. These additional resources include:

- storage
- network / service mesh

Installing Gerrit with the GerritCluster resource is highly recommended over using
the [Gerrit](#gerrit) CustomResource directly, even if only a single deployment is
installed, since this reduces the requirements that have to be managed manually.
The same holds true for the [Receiver](#receiver) CustomResource, which without
a Gerrit instance using the same site provides little value.

For now, only a single Gerrit CustomResource using each [mode](./operator-api-reference.md#gerritmode)
can be deployed in a GerritCluster, e.g. one primary Gerrit and one Gerrit Replica.
The reason for that is, that there is currently no sharding implemented and thus
multiple deployments don't bring any more value than just scaling the existing
deployment. Instead of a primary Gerrit also a Receiver can be installed.

### Gerrit

The Gerrit CustomResource deploys a Gerrit, which can run in multiple modes.

The Gerrit-CustomResource is mainly meant to be used by the GerritCluster-reconciler
to install Gerrit-instances managed by a GerritCluster. Gerrit-CustomResources
can however also be applied separately. Note, that the Gerrit operator will then
not create any storage resources or setup any network resources in addition to
the service.

### GitGarbageCollection

The GitGarbageCollection-CustomResource is used by the operator to set up CronJobs
that regularly run Git garbage collection on the git repositories that are served
by a GerritCluster.

A GitGarbageCollection can either handle all repositories, if no specific repository
is configured or a selected set of repositories. Multiple GitGarbageCollections
can exist as part of the same GerritCluster, but no two GitGarbageCollections
can work on the same project. This is prevented in three ways:

- ValidationWebhooks will prohibit the creation of a second GitGarbageCollection
  that does not specify projects, i.e. that would work on all projects.
- Projects for which a GitGarbageCollections that specifically selects it exists
  will be excluded from the GitGarbageCollection that works on all projects, if
  it exists.
- ValidationWebhooks will prohibit the creation of a GitGarbageCollection that
  specifies a project that was already specified by another GitGarbageCollection.

### Receiver

**NOTE:** A Receiver should never be installed for a GerritCluster that is already
managing a primary Gerrit to avoid conflicts when writing into repositories.

The Receiver-CustomResource installs a Deployment running Apache with a git-http-
backend that is meant to receive pushes performed by Gerrit's replication plugin.
It can only be installed into a GerritCluster that does not include a primary
Gerrit, but only Gerrit Replicas.

The Receiver-CustomResource is mainly meant to be used by the GerritCluster-reconciler
to install a Receiver-instance managed by a GerritCluster. Receiver-CustomResources
can however also be applied separately. Note, that the Gerrit operator will then
not create any storage resources or setup any network resources in addition to
the service.

### GerritNetwork

The GerritNetwork CustomResource deploys network components depending on the
configured ingress provider to enable ingress traffic to GerritCluster components.

The GerritNetwork CustomResource is not meant to be installed manually, but will
be created by the Gerrit Operator based on the GerritCluster CustomResource.

### IncomingReplicationTask

A regularly running task to fetch repositories from a different git server that
is not necessarily a Gerrit to the local Gerrit. This job can also be used to
fetch a subset of refs into an existing repository in Gerrit, e.g. branches from
a forked repository could be fetched into a ref namespace of the fork residing
in Gerrit.

Only fetching via HTTP(S) is supported at the moment. SSH can't be used for
fetches.

## Configuration of Gerrit

The operator takes care of all configuration in Gerrit that depends on the
infrastructure, i.e. Kubernetes and the GerritCluster. This avoids duplicated
configuration and misconfiguration.

This means that some options in the gerrit.config are not allowed to be changed.
If these values are set and are not matching the expected value, a ValidationWebhook
will reject the resource creation/update. Thus, it is best to not set these values
at all. To see which values the operator assigned check the ConfigMap created by
the operator for the respective Gerrit.

These options are:

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

## Feature toggles

This section is dedicated to explain what are the feature toggles and how to set each one of them.

### Cluster Mode

The introduction of this enumeration allows the provision of Gerrit in different modes.

It is defined by an environment variable called `CLUSTER_MODE`, that can have the values of:

* `HIGH_AVAILABILITY`
* `MULTISITE`

By default, the mode is set to `HIGH_AVAILABILITY`.

In case of provision Gerrit in `MULTISITE` mode, only `ISTIO` will be supported as ingress provider.
If the `MULTISITE` mode is used, the following plugins have to be available either
in the container image (not the case by default), in the gerrit.war file in the container image
or in the list of plugins to download as configured in the GerritCluster CustomResource:

- `events-kafka`
- `multisite`
- `pull-replication`
- `websession-broker`

The reason, they are not available in the container images is, that these plugins
are currently not actively maintained under the Apache 2 license. If that changes,
the plugins will be readded to the container images.

It can be configured either by:

#### With helm charts

The environment variable `CLUSTER_MODE` is set by the helm chart property `cluster.mode`.

#### Without helm charts

The environment variable `CLUSTER_MODE` is set in the Operator K8s Deployment Resource.

## Minikube

This chapter gives a short walkthrough in installing the Gerrit operator and a
minimal GerritCluster in minikube. It is meant as an entrypoint for new users and
developers to get familiar with deploying the setup.

### Prerequisites

The following tools are required for following the guide:

- [JDK 17](https://jdk.java.net/archive/)
- [maven](https://maven.apache.org/download.cgi)
- [yq](https://github.com/mikefarah/yq?tab=readme-ov-file#macos--linux-via-homebrew)
- [docker](https://docs.docker.com/get-docker/)
- [minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [helm](https://helm.sh/docs/intro/install/) (> 3.0)

During this guide a lot of components/pods will be started in Minikube. Consider
increasing the number of CPUs and RAM that Minikube is allowed to use. This can
either be done by configuring the limits in Docker or by using the `--cpus=10` and
`--memory=32000` options.

The guide was tested with 10 CPUs and 32G RAM, but should also work with less
resources. With the full setup running without load, 14G RAM and 0.5 CPUs are
being used, but during startup of Gerrit more resources are required.

### Starting Minikube

First start minikube:

```sh
minikube start
```

### Build images

It is highly recommended to build the images manually to ensure that it is
compatible with the example resources at the checked out commit:

```sh
eval $(minikube docker-env)
./build --tag latest
pushd operator
mvn clean install -Drevision=latest
docker image tag gerrit-operator:latest k8sgerrit/gerrit-operator:latest
popd
```

### Gerrit Operator

Then we can install the Gerrit Operator. This setup will use all the default values,
i.e. no ingress provider support will be installed and it will use the latest
version.

```sh
kubectl create ns gerrit-operator

helm dependency build --verify helm-charts/gerrit-operator
helm upgrade \
  --install gerrit-operator \
  helm-charts/gerrit-operator \
  -n gerrit-operator \
  --set=image.imagePullPolicy=IfNotPresent
```

### NFS

For a GerritCluster a volume with ReadWriteMany capabilities is required. Thus,
a NFS provisioner has to be deployed:

```sh
kubectl create ns nfs
helm repo add nfs-ganesha-server-and-external-provisioner \
  https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/
helm upgrade \
  --install nfs \
  nfs-ganesha-server-and-external-provisioner/nfs-server-provisioner \
  -n nfs
```

### Primary Gerrit

The Gerrit Operator will not manage secrets itself, since secret data would be
exposed in the custom resources. Thus, Secrets have to be applied manually and
referenced by name in the GerritCluster CustomResource. To create a Secret
containing SSH keys for Gerrit, run:

```sh
kubectl create ns gerrit
kubectl apply -f Documentation/examples/gerrit.secret.yaml
```

Then a simple GerritCluster can be installed:

```sh
kubectl apply -f Documentation/examples/1-gerritcluster.yaml
```

This will install a single primary Gerrit instance without any networking, i.e.
it can only be accessed via port-forwarding.

### Adding a Gerrit Replica

Next, a Gerrit Replica can be added to the GerritCluster. It will access the
repositories from the same filesystem as the primary Gerrit:

```sh
diff Documentation/examples/1-gerritcluster.yaml Documentation/examples/2-gerritcluster-with-replica.yaml
kubectl apply -f Documentation/examples/2-gerritcluster-with-replica.yaml
```

### Istio

The Gerrit Operator can also manage network routing configuration in the
GerritCluster. To do that we need to install one of the supported Ingress
providers like Istio.

To install Istio with default configuration download istioctl:

```sh
export ISTIO_VERSION=1.20.3 && curl -L https://istio.io/downloadIstio | sh -
export PATH=$PWD/istio-$ISTIO_VERSION/bin:$PATH
```

Then install istio:

```sh
istioctl install -f istio/gerrit.profile.yaml
```

Enabling istio sidecar injection in the Gerrit namespace is required to add Gerrit
pods to the service mesh:

```sh
kubectl label namespace gerrit istio-injection=enabled
```

To make use of Istio, the operator has to be configured to support Istio as an
Ingress provider:

```sh
helm upgrade \
  --install gerrit-operator \
  helm-charts/gerrit-operator \
  -n gerrit-operator \
  --set=ingress.type=ISTIO
```

Next, the Ingress can be enabled in the GerritCluster:

```sh
diff Documentation/examples/2-gerritcluster-with-replica.yaml Documentation/examples/3-gerritcluster-istio.yaml
kubectl apply -f Documentation/examples/3-gerritcluster-istio.yaml
```

To access Gerrit, a tunnel has to be established to the Istio Ingressgateway
service and the host. This should be done in a separate shell session.

```sh
minikube tunnel
```

The connection data can be retrieved like this:

```sh
export INGRESS_HOST=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
export INGRESS_PORT=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.spec.ports[?(@.name=="http2")].port}')
export GATEWAY_URL=$INGRESS_HOST:$INGRESS_PORT
echo $GATEWAY_URL
```

Add the following line to `/etc/hosts` (This assumes that `$GATEWAY_URL` is
`127.0.0.1`):

```sh
127.0.0.1 gerrit.minikube
```

Gerrit can now be accessed using `http://gerrit.minikube`. Note, that since a
primary and a Gerrit Replica exist in the GerritCluster, the Gerrit Operator will
automatically configure Istio to route fetch and clone requests to the Gerrit
Replica.

### High Availability

As a next step Gerrit can be scaled to provide better availability. This is
straightforward for the Gerrit Replica, since it just requires to increase the
number of replicas in the Statefulset of Gerrit primaries in the GerritCluster
spec:

```sh
diff Documentation/examples/3-gerritcluster-istio.yaml  Documentation/examples/4-gerritcluster-ha-replica.yaml
kubectl apply -f Documentation/examples/4-gerritcluster-ha-replica.yaml
```

For the primary Gerrit, the Gerrit Operator will automatically install and configure
the high-availability plugin, if the Primary Gerrit is scaled to 2 or more pod
replicas. However, there are some prerequisites.

First, a database to be used for the global refdb is required. In this example,
zookeeper will be used:

```sh
kubectl create ns zookeeper
kubectl label namespace zookeeper istio-injection=enabled

helm repo add rhcharts https://ricardo-aires.github.io/helm-charts/
helm repo update
helm upgrade --install zookeeper rhcharts/zookeeper \
  --version 0.2.0 \
  --namespace zookeeper \
  --set image.repository=confluentinc/cp-zookeeper \
  --set image.tag=7.3.0 \
  --set replicaCount=1 \
  --set persistence.enabled=false
```

Now, the GerritCluster can be configured to use the Global RefDB. Since the
zookeeper plugin is not maintained in the opensource project at the moment,
configure a URL from where to download the plugin in `Documentation/examples/5-gerritcluster-refdb.yaml`.
Then run:

```sh
diff Documentation/examples/4-gerritcluster-ha-replica.yaml Documentation/examples/5-gerritcluster-refdb.yaml
kubectl apply -f Documentation/examples/5-gerritcluster-refdb.yaml
```

This will configure Gerrit to install the global-refdb lib module and the plugin
with the configured implementation, in this example Zookeeper. The Gerrit Operator
will also set some basic authentication to enable the connection to the database.

For Gerrit to be able to discover its peers, it has to have permissions to get
the Gerrit pods deployed in its own namespace from the Kubernetes API server.
For that purpose, a ServiceAccount with the corresponding role needs to be created:

```sh
kubectl apply -f Documentation/examples/gerrit.rbac.yaml
```

Now, the primary Gerrit can be scaled up (Don't forget to also add the
zookeeper-refdb plugin URL). The ServiceAccount also has to be referenced:

```sh
diff Documentation/examples/5-gerritcluster-refdb.yaml Documentation/examples/6-gerritcluster-ha-primary.yaml
kubectl apply -f Documentation/examples/6-gerritcluster-ha-primary.yaml
```

Now, two primary Gerrit pods are available in the cluster.

## Minikube Multisite

This chapter gives a short walkthrough on installing the Gerrit operator and a
GerritCluster in minikube with three primary instances. The features currently
implemented are:

* Use pull-replication plugin.
* Each primary has its own file system for Gerrit site (no nfs).
* Number of primaries can change but auto scaling is not supported.

> **NOTE**: Gerrit multi-site is widely adopted and fully production-ready.
> However, the work is never complete as it can still be evolved and improved
> over time. Therefore, this implementation is still to be considered a
> _work-in-progress_ albeit fully working E2E for a production environment.

### Prerequisites

Prerequisites for this configuration are the same as [this section](#prerequisites-1),
bearing in mind that some of them may not be strictly relevant, plus:

* Kafka broker should be deployed or accessible from the cluster. The connection string must be
specified in the `events-kafka plugin` section of the `gerrit.config`, located within the
`spec.gerrits[0].spec.configFiles` object, i.e:
```
      configFiles:
        gerrit.config: |-
        ...
            [plugin "events-kafka"]
              bootstrapServers = kafka-service.kafka-zk.svc.cluster.local:9092
        ...
```


For additional context, please refer to the section `configFiles` in [GerritTemplateSpec](operator-api-reference.md#gerrittemplatespec)
and [events-kafka plugin documentation](https://gerrit.googlesource.com/plugins/events-kafka/+/refs/heads/master/src/main/resources/Documentation/config.md)

* Zookeeper should be deployed or accessible from the cluster. The connection string is defined
within the `spec.refdb` object, i.e:
```
  refdb:
    database: ZOOKEEPER
    zookeeper:
      connectString: zookeeper-service.kafka-zk.svc.cluster.local:2181
```
For additional context, please refer to the section [GlobalRefDbConfig](operator-api-reference.md#globalrefdbconfig).

### Build images

Please read the previous section [Build images](#build-images).

### Istio

Please read the previous section [Istio](#istio).

### Gerrit Operator

To install the operator in the 'multisite' cluster mode please note that the value of the property
`cluster.mode` in the file `helm-charts/gerrit-operator/values.yaml`
should be set to `MULTISITE`.

Deploy the operator:

```sh
kubectl create ns gerrit-operator
helm dependency build --verify helm-charts/gerrit-operator
helm upgrade \
  --install gerrit-operator \
  helm-charts/gerrit-operator \
  -n gerrit-operator \
  --set=ingress.type=ISTIO \
  --set=cluster.mode=MULTISITE
```

### Deploy multisite with three replicas

Deploy GerritCluster custom resource that:

* Creates a StatefulSet with three nodes
* Configure networking and traffic management for Http and Ssh
* Set up pull replication configuration

```sh
kubectl create ns gerrit
kubectl label namespace gerrit istio-injection=enabled
kubectl apply -f Documentation/examples/gerritcluster-roles.yaml
kubectl apply -f Documentation/examples/gerritcluster-3-nodes-pr-kafka-multisite.yaml
```
To access Gerrit, a tunnel has to be established to the Istio Ingressgateway
service and the host. This should be done in a separate shell session.

```sh
minikube tunnel
```

Please note that in this example the host is defined as `gerrit.multisite.com` in the
property `spec.ingress.host`. That implies to modify the `/etc/hosts` as follow:

```
127.0.0.1       localhost gerrit.multisite.com
```

## Multisite notes

This chapter is intended as a collection of resources for the usage of the operator
with the the property `multisite.enabled` set to true, not necessarily in minikube.

### Istio

When using the operator in a proper kubernetes cluster (not minikube) with multiple nodes, please
make sure to patch the gatway as follow:

```sh
kubectl patch svc istio-ingressgateway -n istio-system -p '{"spec":{"externalTrafficPolicy":"Local"}}'
```

Please read more on how to preserve the original client source IP on the [istio ingress](https://istio.io/latest/docs/tasks/security/authorization/authz-ingress/)

### Kafka

This is an example of a kafka deployment:

```
kubectl create ns kafka
kubectl label namespace kafka istio-injection=enabled

helm repo add rhcharts https://ricardo-aires.github.io/helm-charts/
helm repo update
helm upgrade --install kafka rhcharts/kafka \
  --version 0.2.0 \
  --namespace kafka \
  --set image.repository=confluentinc/cp-kafka \
  --set image.tag=7.3.0 \
  --set replicaCount=1 \
  --set persistence.enabled=false \
  --set configurationOverrides."min.insync.replicas"=1 \
  --set configurationOverrides."default.replication.factor"=1 \
  --set configurationOverrides."offsets.topic.replication.factor"=1 \
  --set configurationOverrides."transaction.state.log.replication.factor"=1 \
  --set configurationOverrides."transaction.state.log.min.isr"=1
```

To connect and excecute operations in kafka, please spin up an image as client:
```sh
kubectl \
  --namespace kafka \
  exec \
  --stdin \
  --tty \
  kafka-0 -- \
  /bin/kafka-topics \
  --bootstrap-server kafka-headless.kafka.svc.cluster.local:9092 \
  /bin/bash
```

After that it is possible to connect to the image to test consuming of the messages in a topic, i.e.
for the `gerrit` topic:
```sh
kubectl \
  --namespace kafka \
  exec \
  --stdin \
  --tty \
  kafka-0 -- \
  /bin/kafka-topics \
  --bootstrap-server kafka-headless.kafka.svc.cluster.local:9092 \
  --from-beginning \
  --topic gerrit
```

Or to list all the topics:
```sh
kubectl \
  --namespace kafka \
  exec \
  --stdin \
  --tty \
  kafka-0 -- \
  /bin/kafka-topics \
  --bootstrap-server kafka-headless.kafka.svc.cluster.local:9092 \
  --list
```

Or to list the group_ids:
```sh
kubectl \
  --namespace kafka \
  exec \
  --stdin \
  --tty \
  kafka-0 -- \
  /bin/kafka-consumer-groups \
  --bootstrap-server kafka-headless.kafka.svc.cluster.local:9092 \
  --list
```

This is how to create all the necessary topics:

```sh
for t in stream_event_minikube-gerrit index_event_minikube-gerrit web_session_minikube-gerrit batch_index_minikube-gerrit cache_eviction_minikube-gerrit list_project_minikube-gerrit; do \
  kubectl exec -n kafka -it kafka-0 -- /bin/kafka-topics \
  --bootstrap-server kafka-headless.kafka.svc.cluster.local:9092 \
  --create --topic "$t" --replication-factor 1 --partitions 1; \
done
```

### Zookeeper

An example of kafka deployment can be found
[here](https://github.com/lydtechconsulting/kafka-kubernetes-demo/blob/v1.0.0/resources/zookeeper.yml),
making sure to set the namespace as described in the [Prerequisites](#prerequisites-2).

To connect and excecute operations in zookeeper, for example to check the sha stored in globalrefdb
for the meta ref for the change 1, on the `poc-demo` project
`/gerrit/gerrit/poc-demo/refs/changes/01/1/meta` you can use this command:
```sh
kubectl \
  --namespace gerrit \
  exec \
  --stdin \
  --tty \
  my-kafka -- \
  zookeeper-shell \
  zookeeper-service.kafka-zk.svc.cluster.local:2181 \
  get /gerrit/gerrit/poc-demo/refs/changes/01/1/meta
```

### Sync Gerrit sites

Because in a multisite environment, each gerrit instance is initialized during the `gerrit-init`
phase, the status of the repositories, in particular, the `All-Users` and `All-Projects` is not
consistent across the nodes. To fix this, a manual operation to sync all the sites is required. We
can leverage pull replication to do this, i.e.:

* Forward port 8080 for Gerrit-1 (without passing by the ingress-gatway):
  ```sh
  kubectl port-forward gerrit-1 8080:8080 -n gerrit
  ```

* Add the ssh key to gerrit-1
  ```sh
  curl -v -X POST -H "Content-Type: text/plain" --user admin:secret --data "$(cat ~/.ssh/id_ed25519.pub)" http://localhost:8080/a/accounts/self/sshkeys
  ```

* Forward the port 29418 to gerrit-1
  ```sh
  kubectl port-forward gerrit-1 29418:29418 -n gerrit
  ```

* Start pull replication for Gerrit-1 (pull from gerrit-0)
  ```sh
  ssh -p 29418 admin@localhost pull-replication start --url gerrit-0 --all
  ```

And finally repeat the process for gerrit-2.
You can also log in to each gerrit node and make sure that all the refs have the same sha in the
`All-Projects` and `All-Users` repositories with the command `git show-ref`.

## Multisite monitoring

This chapter provides an example to provision the [Prometheus stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack) in the Minikube cluster.
This stack allows to define [Grafana dashboards](https://grafana.com/grafana/dashboards/), and query
metrics via [Prometheus](https://prometheus.io/) using [PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/) and to define alerts via [AlerManager](https://prometheus.io/docs/alerting/latest/alertmanager/).

### Prerequisites

1. Install the `Prometheus stack Helm chart` locally:

```sh
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

2. Deploy the `Prometheus stack`:

```sh
kubectl create ns monitoring && \
helm install my-kubpromstack prometheus-community/kube-prometheus-stack -n monitoring \
--set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
--set prometheus.prometheusSpec.probeSelectorNilUsesHelmValues=false \
--set prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false \
--set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
--set defaultRules.create=false \
--set grafana.defaultDashboardsEnabled=false
```

3. Deploy `PodMonitor` Custom Resource to allow `Prometheus` to scrap Gerrit metrics from the
different Gerrit instnces:
```sh
apiVersion: v1
kind: Secret
metadata:
  name: gerrit-metric-bearer-token
  namespace: gerrit
type: Opaque
data:
  bearer-token: Secret
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: gerrit-multisite
  namespace: gerrit
  labels:
    team: devops
spec:
  selector:
    matchLabels:
      app.kubernetes.io/component: gerrit-statefulset-gerrit
      app.kubernetes.io/instance: gerrit
      app.kubernetes.io/managed-by: gerrit-operator
      app.kubernetes.io/name: gerrit
      app.kubernetes.io/part-of: gerrit
  podMetricsEndpoints:
  - port: http
    path: /plugins/metrics-reporter-prometheus/metrics
    bearerTokenSecret:
      name: gerrit-metric-bearer-token
      key: bearer-token
```

### Access to Prometheus

```sh
kubectl port-forward prometheus-my-kubpromstack-kube-prome-prometheus-0 9090:9090 -n monitoring
```

### Access to Grafana

```sh
kubectl port-forward my-kubpromstack-grafana-5f8bcc9786-r6c8b  3000:3000 -n monitoring
```
Note: please check the `Grafana` pod name

### Access to AlertManager

```sh
kubectl port-forward alertmanager-my-kubpromstack-kube-prome-alertmanager-0 9093:9093 -n monitoring
```
