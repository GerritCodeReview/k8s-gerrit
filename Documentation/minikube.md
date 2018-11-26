# Running Gerrit on Kubernetes using Minikube

To test Gerrit on Kubernetes without first have to get a Kubernetes cluster in
the cloud, a local one-node cluster can be set up using Minikube. Minikube provides
basic Kubernetes functionality and allows to quickly deploy and evaluate a
Kubernetes deployment.
This tutorial will guide through setting up Minikube and deploying the gerrit-
master and gerrit-slave helm charts to it. Note, that due to probably limited
compute resources on a single local machine and the restricted functionality
(compared to Kubernetes) of Minikube, the full functionality of the charts might
not be usable.

## Installing Kubectl and Minikube

To use Minikube. a hypervisor is needed. A good non-commercial solution is HyperKit.
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

To start a Minikube cluster simply run:

```sh
minikube start
```

If a hypervisor driver other than virtual box (e.g. hyperkit) is used, set the
`--vm-driver` option accordingly:

```sh
minikube config vm-driver hyperkit
```

The gerrit-master and gerrit-slave charts require more resources than the default
limits configured for Minikube. Configure the resource limits using:

```sh
minikube config memory 4096
minikube config cpus 4
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
Ingress definition needs to be redirected to Minikube:

```sh
echo "$(minikube ip) master.gerrit backend.gerrit slave.gerrit" | sudo tee -a /etc/hosts
```

The host names (e.g. `master.gerrit`) are the defaults, when using the values.yaml
files provided as and example for minikube. Change them accordingly, if a different
one is chosen.
This will only redirect traffic from the computer running Minikube.

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

***note
The `gerrit.sh start` command may return `FAILED`. Nevertheless, when testing the
setup, Gerrit started up anyway after some time.
***

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
  | mysql-bin.000004 | 446      |              |                  |                   |
  +------------------+----------+--------------+------------------+-------------------+
```

Further, a dump of the reviewdb is needed. Exit mysql and run:

```sh
mysqldump -u root -p --databases reviewdb > master_dump.sql
```

To get the dump to the host, exit the pod and run:

```sh
k cp gerrit-master-mysql-<id>:/master_dump.sql master_dump.sql
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
k cp master_dump.sql gerrit-slave-mysql-replication-init-<id>:/var/data/db/master_dump.sql
```

The status of database initialization can be followed by running:

```sh
k logs -f gerrit-slave-mysql-replication-init-<id>
```

As soon as the status of the database slave is displayed, the database should be
ready.

As a next step the `All-Projects` and `All-Users` repositories have to be created
on the slave by running:

```sh
curl -L http://backend.gerrit/new/All-Projects.git
curl -L http://backend.gerrit/new/All-Users.git
```

Afterwards, the slave will start up, which can be followed by running:

```sh
k logs -f gerrit-slave-gerrit-slave-deployment-<id>
```