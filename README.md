# Gerrit Deployment on Kubernetes

Container images, configurations, [helm charts](https://helm.sh/) and a Kubernetes
Operator for installing [Gerrit](https://www.gerritcodereview.com/) on
[Kubernetes](https://kubernetes.io/).

# Deploying Gerrit on Kubernetes

This project provides helm-charts to install Gerrit either as a primary instance
or a replica on Kubernetes.

The helm-charts are located in the `./helm-charts`-directory. Currently, the
charts are not published in a registry and have to be deployed from local sources.

For a detailed guide of how to install the helm-charts refer to the respective
READMEs in the helm-charts directories:

- [gerrit](helm-charts/gerrit/README.md)
- [gerrit-replica](helm-charts/gerrit-replica/README.md)

These READMEs detail the prerequisites required by the charts as well as all
configuration options currently provided by the charts.

To evaluate and test the helm-charts, they can be installed on a local machine
running Minikube. Follow this [guide](Documentation/minikube.md) to get a detailed
description how to set up the Minikube cluster and install the charts.

Alternatively, a Gerrit Operator can be used to install and operate Gerrit in a
Kubernetes cluster. The [documentation](./Documentation/operator.md) describes
how to build and deploy the Gerrit Operator and how to use it to install Gerrit.

# Docker images

This project provides the sources for docker images used by the helm-charts.
The images are also provided on [Dockerhub](https://hub.docker.com/u/k8sgerrit).

The project also provides scripts to build and publish the images so that custom
versions can be used by the helm-charts. This requires however a docker registry
that can be accessed from the Kubernetes cluster, on which Gerrit will be
deployed. The functionality of the scripts is described in the following sub-
sections.

## Building images

To build all images, the `build`-script in the root directory of the project can
be used:

```
./build
```

If a specific image should be built, the image name can be specified as an argument.
Multiple images can be specified at once:

```
./build gerrit git-gc
```

The build-script usually uses the `latest`-tag to tag the images. By using the
`--tag TAG`-option, a custom tag can be defined:

```
./build --tag test
```

The version of Gerrit built into the images can be changed by providing a download
URL for a `.war`-file containing Gerrit:

```
./build --gerrit-url https://example.com/gerrit.war
```

The version of a health-check plugin built into the images can be changed by
providing a download URL for a `.jar`-file containing the plugin:

```
./build --healthcheck-jar-url https://example.com/healthcheck.jar
```

The build script will in addition tag the image with the output of
`git describe --dirty`.

The target platform for the image build can be specified by using the `--platform PLATFORM` option.
This allows you to build images for different architectures such as ARM or x86. By default, the
platform is set to linux/amd64.

```
./build --platform linux/arm64
```

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

The `<component-name>` is one of: `apache-git-http-backend`, `git-gc`, `gerrit`
or `gerrit-init`.

Adding the `--update-latest`-flag will also update the images tagged `latest` in
the repository:

```
./publish --update-latest <component-name>
```

## Running images in Docker

The container images are meant to be used by the helm-charts provided in this
project. The images are thus not designed to be used in a standalone setup. To
run Gerrit on Docker use the
[docker-gerrit](https://gerrit-review.googlesource.com/admin/repos/docker-gerrit)
project.

# Running tests

The tests are implemented using Python and `pytest`. To ensure a well-defined
test-environment, `pipenv` is meant to be used to install packages and provide a
virtual environment in which to run the tests. To install pipenv, use `brew`:

```sh
brew install pipenv
```

More detailed information can be found in the
[pipenv GitHub repo](https://github.com/pypa/pipenv).

To create the virtual environment with all required packages, run:

```sh
pipenv install
```

To run all tests, execute:

```sh
pipenv run pytest -m "not smoke"
```

***note
The `-m "not smoke"`-option excludes the smoke tests, which will fail, since
no Gerrit-instance will be running, when they are executed.
***

Some tests will need to create files in a temporary directory. Some of these
files will be mounted into docker containers by tests. For this to work make
either sure that the system temporary directory is accessible by the Docker
daemon or set the base temporary directory to a directory accessible by Docker
by executing:

```sh
pipenv run pytest --basetemp=/tmp/k8sgerrit -m "not smoke"
```

By default the tests will build all images from scratch. This will greatly
increase the time needed for testing. To use already existing container images,
a tag can be provided as follows:

```sh
pipenv run pytest --tag=v0.1 -m "not smoke"
```

The tests will then use the existing images with the provided tag. If an image
does not exist, it will still be built by the tests.

By default the build of the container images will not use the build cache
created by docker. To enable the cache, execute:

```sh
pipenv run pytest --build-cache -m "not smoke"
```

Slow tests may be marked with the decorator `@pytest.mark.slow`. These tests
may then be skipped as follows:

```sh
pipenv run pytest --skip-slow -m "not smoke"
```

There are also other marks, allowing to select tests (refer to
[this section](#test-marks)).

To run specific tests, execute one of the following:

```sh
# Run all tests in a directory (including subdirectories)
pipenv run pytest tests/container-images/base

# Run all tests in a file
pipenv run pytest tests/container-images/base/test_container_build_base.py

# Run a specific test
pipenv run \
  pytest tests/container-images/base/test_container_build_base.py::test_build_base

# Run tests with a specific marker
pipenv run pytest -m "docker"
```

For a more detailed description of how to use `pytest`, refer to the
[official documentation](https://docs.pytest.org/en/latest/contents.html).

## Test marks

### docker

Marks tests which start up docker containers. These tests will interact with
the containers by either using `docker exec` or sending HTTP-requests. Make
sure that your system supports this kind of interaction.

### incremental

Marks test classes in which the contained test functions have to run
incrementally.

### integration

Marks integration tests. These tests test interactions between containers,
between outside clients and containers and between the components installed
by a helm chart.

### kubernetes

Marks tests that require a Kubernetes cluster. These tests are used to test the
functionality of the helm charts in this project and the interaction of the
components installed by them. The cluster should not be used for other purposes
to minimize unforeseen interactions.

These tests require a storage class with ReadWriteMany access mode within the
cluster. The name of the storage class has to be provided with the
`--rwm-storageclass`-option (default: `shared-storage`).

### slow

Marks tests that need an above average time to run.

### structure

Marks structure tests. These tests are meant to test, whether certain components
exist in a container. These tests ensure that components expected by the users
of the container, e.g. the helm charts, are present in the containers.

## Running smoke tests

To run smoke tests, use the following command:

```sh
pipenv run pytest \
  -m "smoke" \
  --basetemp="<tmp-dir for tests>" \
  --ingress-url="<Gerrit URL>" \
  --gerrit-user="<Gerrit user>" \
  --gerrit-pwd
```

The smoke tests require a Gerrit user that is allowed to create and delete
projects. The username has to be given by `--gerit-user`. Setting the
`--gerrit-pwd`-flag will cause a password prompt to enter the password of the
Gerrit-user.

# Contributing

Contributions to this project are welcome. If you are new to the Gerrit workflow,
refer to the [Gerrit-documentation](https://gerrit-review.googlesource.com/Documentation/intro-gerrit-walkthrough.html)
for guidance on how to contribute changes.

The contribution guidelines for this project can be found
[here](Documentation/developer-guide.md).

# Roadmap

The roadmap of this project can be found [here](Documentation/roadmap.md).

Feature requests can be made by pushing a change for the roadmap. This can also
be done to announce/discuss features that you would like to provide.

# Contact

The [Gerrit Mailing List](https://groups.google.com/forum/#!forum/repo-discuss)
can be used to post questions and comments on this project or Gerrit in general.
