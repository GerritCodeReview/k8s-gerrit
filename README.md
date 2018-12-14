# Gerrit Deployment on Kubernetes

Container images, configurations and [Helm](https://helm.sh/) charts for installing
[Gerrit](https://www.gerritcodereview.com/) on [Kubernetes](https://kubernetes.io/).

# Docker images

Images to run a Gerrit master and slave setup based on the latest stable-2.12 Gerrit build.

## Building images

To build all images, the `build`-script in the root directory of the project can
be used:

```
./build
```

If a specific image should be build, the image name can be specified as an argument.
Multiple images can be specified at once:

```
./build gerrit-slave git-gc
```

The build-script usually uses the `latest`-tag to tag the images. By using the
`--tag TAG`-option, a custom tag can be defined:

```
./build --tag test
```

The build script will in addition tag the image with the output of
`git describe --dirty`.

The single component images inherit a base image. The `Dockerfile` for the base
image can be found in the `./base`-directory. It will be
automatically built by the `./build`-script. If the component images are built
manually, the base image has to be built first with the target
`base:latest`, since it is not available in a registry and thus has
to exist locally.

## Publishing images

The publish script in the root directory of the project can be used to push the
built images to the configured registry. To do so, log in first, before executing
the script.

```
docker login <registry>
```

To configure the registry and image version, the respective values can be
configured via env variables `REGISTRY` and `TAG`. In addition, these values can
also be passed as command line options named `--registry` and `--tag` in which
case they override the values from env variables:

```
./publish <component-name>
```

The `<component-name>` is one of: `apache-git-http-backend`, `git-gc`,
`gerrit-slave`.

Adding the `--update-latest`-flag will also update the images tagged `latest` in
the repository:

```
./publish --update-latest <component-name>
```

## Running images

Assuming a Gerrit site already exists, is located at `/path/to/gerrit-slave` and
owned by the `gerrit`-user defined in the docker image (default `UID: 1000`) run
the following command for each image in the directories containing the respective
docker image:

```
./start /path/to/gerrit-slave <component-name>
```

The `<component-name>` is one of: `apache-git-http-backend`, `git-gc`,
`gerrit-slave`, `gerrit-master`, `gerrit-init`.

If a specific version of the image should be used, the `--tag TAG`-option can be
used to provide the image tag:

```
./start /path/to/gerrit-slave --tag d4fad48 <component-name>
```

or define the tag as an env variable:

```
export TAG=d4fad48
./start /path/to/gerrit-slave <component-name>
```

To detach the running container from the shell, use the `--detach`-flag:

```
./start --detach /path/to/gerrit-slave <component-name>
```

## Important notes

Currently, java is installed under `/usr/lib/jvm/java-8-openjdk-amd64/jre`.
Therefore, make sure that `container.javaHome` is set to that path in the `gerrit.config`:
```
  javaHome = /usr/lib/jvm/java-8-openjdk-amd64/jre
```

The mysql-replication-init docker image is only required for setting up the Gerrit
slave on Kubernetes. If deploying the Gerrit slave outside of Kubernetes, it can
be ignored.

# Helm Charts

These Helm charts can be used to install a Gerrit cluster consisting of a
Gerrit master and a Gerrit slave on a Kubernetes cluster.

To evaluate and test the helm-charts, they can be installed on a local machine
running Minikube. Follow this [guide](Documentation/minikube.md) to get a detailed
description how to set up the Minikube cluster and install the charts.

## File System Storage

Currently this deployment uses NFS, some options:

* Create an EFS volume on AWS
* Install a NFS server on Kubernetes cluster which doesn't have read-write-many
Persistent Volumes available using
[NFS-provisioner](helm-charts/gerrit-master/docs/nfs-provisioner.md)

## Gerrit Master

* Install a [MySQL master](helm-charts/gerrit-master/docs/mysqld.md)
* Install a [Gerrit master](helm-charts/gerrit-master/README.md)

## Gerrit Slave

* Install a [MySQL slave](helm-charts/gerrit-slave/docs/mysqld.md)
* Install a [Gerrit slave](helm-charts/gerrit-slave/README.md)
