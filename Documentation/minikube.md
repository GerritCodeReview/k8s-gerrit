# Running Gerrit on Kubernetes using Minikube

To test Gerrit on Kubernetes locally, a one-node cluster can be set up using
Minikube. Minikube provides basic Kubernetes functionality and allows to quickly
deploy and evaluate a Kubernetes deployment.
This tutorial will guide through setting up Minikube to deploy the gerrit and
gerrit-replica helm charts to it. Note, that due to limited compute
resources on a single local machine and the restricted functionality of Minikube,
the full functionality of the charts might not be usable.

## Installing Kubectl and Minikube

To use Minikube, a hypervisor is needed. A good non-commercial solution is HyperKit.
The Minikube project provides binaries to install the driver:

```sh
curl -LO https://storage.googleapis.com/minikube/releases/latest/docker-machine-driver-hyperkit \
  && sudo install -o root -g wheel -m 4755 docker-machine-driver-hyperkit /usr/local/bin/
```

To manage Kubernetes clusters, the Kubectl CLI tool will be needed. A detailed
guide how to do that for all supported OSs can be found
[here](https://kubernetes.io/docs/tasks/tools/install-kubectl/#install-with-homebrew-on-macos).
On OSX hombrew can be used for installation:

```sh
brew install kubernetes-cli
```

Finally, Minikube can be installed. Download the latest binary
[here](https://github.com/kubernetes/minikube/releases). To install it on OSX, run:

```sh
VERSION=1.1.0
curl -Lo minikube https://storage.googleapis.com/minikube/releases/v$VERSION/minikube-darwin-amd64 && \
  chmod +x minikube && \
  sudo cp minikube /usr/local/bin/ && \
  rm minikube
```

## Starting a Minikube cluster

For a more detailed overview over the features of Minikube refer to the
[official documentation](https://kubernetes.io/docs/setup/minikube/). If a
hypervisor driver other than virtual box (e.g. hyperkit) is used, set the
`--vm-driver` option accordingly:

```sh
minikube config set vm-driver hyperkit
```

The gerrit and gerrit-replica charts are configured to work with the default
resource limits configured for minikube (2 cpus and 2Gi RAM). If more resources
are desired (e.g. to speed up deployment startup or for more resource intensive
tests), configure the resource limits using:

```sh
minikube config set memory 4096
minikube config set cpus 4
```

To install a full Gerrit and Gerrit replica setup with reasonable startup
times, Minikube will need about 9.5 GB of RAM and 3-4 CPUs! But the more the
better.

To start a Minikube cluster simply run:

```sh
minikube start
```

Starting up the cluster will take a while. The installation should automatically
configure kubectl to connect to the Minikube cluster. Run the following command
to test whether the cluster is up:

```sh
kubectl get nodes

NAME       STATUS   ROLES    AGE   VERSION
minikube   Ready    master   1h    v1.14.2
```

The helm-charts use ingresses, which can be used in Minikube by enabling the
ingress addon:

```sh
minikube addons enable ingress
```

Since for testing there will probably no usable host names configured to point
to the minikube installation, the traffic to the hostnames configured in the
Ingress definition needs to be redirected to Minikube by editing the `/etc/hosts`-
file, adding a line containing the Minikube IP and a whitespace-delimited list
of all the hostnames:

```sh
echo "$(minikube ip) primary.gerrit backend.gerrit replica.gerrit" | sudo tee -a /etc/hosts
```

The host names (e.g. `primary.gerrit`) are the defaults, when using the values.yaml
files provided as and example for minikube. Change them accordingly, if a different
one is chosen.
This will only redirect traffic from the computer running Minikube.

To see whether all cluster components are ready, run:

```sh
kubectl get pods --all-namespaces
```

The status of all components should be `Ready`. The kubernetes dashboard giving
an overview over all cluster components, can be opened by executing:

```sh
minikube dashboard
```

## Install helm

Helm is needed to install and manage the helm charts. To install the helm client
on your local machine (running OSX), run:

```sh
brew install kubernetes-helm
```

A guide for all suported OSs can be found [here](https://docs.helm.sh/using_helm/#installing-helm).

## Start an NFS-server

The helm-charts need a volume with ReadWriteMany access mode to store
git-repositories. This guide will use the nfs-server-provisioner chart to provide
NFS-volumes directly in the cluster. A basic configuration file for the nfs-server-
provisioner-chart is provided in the supplements-directory. It can be installed
by running:

```sh
helm install nfs \
  stable/nfs-server-provisioner \
  -f ./supplements/nfs.minikube.values.yaml
```

## Installing the gerrit helm chart

A configuration file to configure the gerrit chart is provided at
`./supplements/gerrit.minikube.values.yaml`. To install the gerrit
chart on Minikube, run:

```sh
helm install gerrit \
  ./helm-charts/gerrit \
  -f ./supplements/gerrit.minikube.values.yaml
```

Startup may take some time, especially when allowing only a small amount of
resources to the containers. Check progress with `kubectl get pods -w` until
it says that the pod `gerrit-gerrit-stateful-set-0` is `Running`.
Then use `kubectl logs -f gerrit-gerrit-stateful-set-0` to follow
the startup process of Gerrit until a line like this shows that Gerrit is ready:

```sh
[2019-06-04 15:24:25,914] [main] INFO  com.google.gerrit.pgm.Daemon : Gerrit Code Review 2.16.8-86-ga831ebe687 ready
```

To open Gerrit's UI, run:

```sh
open http://primary.gerrit
```

## Installing the gerrit-replica helm chart

A custom configuration file to configure the gerrit-replica chart is provided at
`./supplements/gerrit-replica.minikube.values.yaml`. Install it by running:

```sh
helm install gerrit-replica \
  ./helm-charts/gerrit-replica \
  -f ./supplements/gerrit-replica.minikube.values.yaml
```

The replica will start up, which can be followed by running:

```sh
kubectl logs -f gerrit-replica-gerrit-replica-deployment-<id>
```

Replication of repositories has to be started on the Gerrit, e.g. by making
a change in the respective repositories. Only then previous changes to the
repositories will be available on the replica.

## Cleanup

Shut down minikube:

```sh
minikube stop
```

Delete the minikube cluster:

```sh
minikube delete
```

Remove the line added to `/etc/hosts`. If Minikube is restarted, the cluster will
get a new IP and the `/etc/hosts`-entry has to be adjusted.
