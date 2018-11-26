# Running Gerrit on Kubernetes using Minikube

To test Gerrit on Kubernetes locally, a one-node cluster can be set up using
Minikube. Minikube provides basic Kubernetes functionality and allows to quickly
deploy and evaluate a Kubernetes deployment.
This tutorial will guide through setting up Minikube to deploy the gerrit-
master and gerrit-slave helm charts to it. Note, that due to limited compute
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
curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.30.0/minikube-darwin-amd64 && \
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

The gerrit-master and gerrit-slave charts are configured to work with the default
resource limits configured for minikube (2 cpus and 2Gi RAM). If more resources
are desired (e.g. to speed up deployment startup or for more resource intensive
tests), configure the resource limits using:

```sh
minikube config set memory 4096
minikube config set cpus 4
```

To install a full Gerrit master and Gerrit slave setup with reasonable startup
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
minikube   Ready    master   1h    v1.10.0
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
echo "$(minikube ip) master.gerrit backend.gerrit slave.gerrit" | sudo tee -a /etc/hosts
```

The host names (e.g. `master.gerrit`) are the defaults, when using the values.yaml
files provided as and example for minikube. Change them accordingly, if a different
one is chosen.
This will only redirect traffic from the computer running Minikube.

To see whether all cluster components are ready, run:

```sh
kubectl get pos --all-namespaces
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

For helm being able to install charts on a Kubernetes cluster, it needs Tiller
installed. To do that, run:

```sh
helm init
```

## Start an NFS-server

The helm-charts need a volume with ReadWriteMany access mode to store
git-repositories. This guide will use the nfs-server-provisioner chart to provide
NFS-volumes directly in the cluster. A basic configuration file for the nfs-server-
provisioner-chart is provided in the supplements-directory. It can be installed
by running:

```sh
helm install stable/nfs-server-provisioner \
  --name nfs \
  -f ./supplements/nfs.minikube.values.yaml
```

## Installing the gerrit-master helm chart

A configuration file to configure the gerrit-master chart is provided at
`./supplements/gerrit-master.minikube.values.yaml`. To install the gerrit-master
chart on Minikube, run:

```sh
helm install ./helm-charts/gerrit-master \
  -f ./supplements/gerrit-master.minikube.values.yaml \
  --dep-up \
  -n gerrit-master
```

Startup may take some time, especially when allowing only a small amount of
resources to the containers. Check progress with `kubectl get pods -w` until
it says that the pod `gerrit-master-gerrit-master-deployment-<id>` is `Running`.
Then use `kubectl logs -f gerrit-master-gerrit-master-deployment-<id>` to follow
the startup process of Gerrit until a line like this shows that Gerrit is ready:

```sh
[2018-11-27 09:58:52,066] [main] INFO  com.google.gerrit.pgm.Daemon : Gerrit Code Review 2.16-18-ge42b76d4ba ready
```

***note
The `gerrit.sh start` command may return `FAILED`. Nevertheless, when testing the
setup, Gerrit started up anyway after some time. This happens, if due to low
resources Gerrit needs too long to start up and the `gerrit.sh`-scripts runs into
a timeout.
***

To open Gerrit's UI, run:

```sh
open http://master.gerrit
```

## Installing the gerrit-slave helm chart

Before installing the gerrit-slave chart, some information and configuration is
needed from the master's database. First find out the name of the pod running
the mysql database of the master. It can be looked up by running:

```sh
kubectl get pods
```

It should have the format `gerrit-master-mysql-<id>`. Then exec the pod and log
into the database by executing:

```sh
kubectl exec -it gerrit-master-mysql-<id> bash
mysql -u root -pbig_secret
```

Create a user for the database replication:

```sql
CREATE USER 'repl' IDENTIFIED BY 'password';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'
  IDENTIFIED BY 'password';
FLUSH PRIVILEGES;
```

Get the current position of the transaction logs by executing the following SQL
statement:

```sql
SHOW MASTER STATUS;

  +------------------+----------+--------------+------------------+-------------------+
  | File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
  +------------------+----------+--------------+------------------+-------------------+
  | mysql-bin.000004 | 4918     |              |                  |                   |
  +------------------+----------+--------------+------------------+-------------------+
```

Further, a dump of the reviewdb is needed. Exit mysql by typing `quit` and run:

```sh
mysqldump -u root -p --databases reviewdb > master_dump.sql
```

To get the dump to the host, exit the pod and run:

```sh
kubectl cp gerrit-master-mysql-<id>:/master_dump.sql master_dump.sql
```

A custom configuration file to configure the gerrit-slave chart is provided at
`./supplements/gerrit-slave.minikube.values.yaml`. Change the values of
`database.replication.mysql.config.masterLogFile` and
`database.replication.mysql.config.masterLogPos` to the values retrieved beforehand.
Then, the gerrit-slave chart can be started:

```sh
helm install ./helm-charts/gerrit-slave \
  -f ./supplements/gerrit-slave.minikube.values.yaml \
  --dep-up \
  -n gerrit-slave
```

When the `gerrit-slave-mysql-<id>`-pod and the `gerrit-slave-mysql-replication-init-<id>`-
pod are ready, provide the database dump to the database by executing:

```sh
kubectl cp master_dump.sql gerrit-slave-mysql-replication-init-<id>:/var/data/db/master_dump.sql
```

The status of database initialization can be followed by running:

```sh
kubectl logs -f gerrit-slave-mysql-replication-init-<id>
```

As soon as the status of the database slave is displayed, the database should be
ready.

As a next step the `All-Projects` and `All-Users` repositories have to be created
on the slave by running:

```sh
curl -L -u git http://backend.gerrit/new/All-Projects.git
curl -L -u git http://backend.gerrit/new/All-Users.git
```

Afterwards, the slave will start up, which can be followed by running:

```sh
kubectl logs -f gerrit-slave-gerrit-slave-deployment-<id>
```

Replication of repositories has to be started on the Gerrit master, e.g. by making
a change in the respective repositories. Only then previous changes to the
repositories will be available on the slave.

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