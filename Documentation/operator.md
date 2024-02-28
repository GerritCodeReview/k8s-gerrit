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
