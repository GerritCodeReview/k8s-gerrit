# Gerrit Deployment on Kubernetes

Container images, configurations and a Kubernetes Operator for installing
[Gerrit](https://www.gerritcodereview.com/) on [Kubernetes](https://kubernetes.io/).

# Deploying Gerrit on Kubernetes

A Gerrit Operator can be used to install and operate Gerrit in a
Kubernetes cluster. The [documentation](./Documentation/operator.md) describes
how to build and deploy the Gerrit Operator and how to use it to install Gerrit.

# Docker images

This project provides the sources for docker images used by the Kubernetes
deployment. The images are also provided on [Dockerhub](https://hub.docker.com/u/k8sgerrit).

The project also provides scripts to build and publish the images so that custom
versions can be used. This requires however a docker registry that can be accessed
from the Kubernetes cluster, on which Gerrit will be deployed. The functionality
of the scripts is described in the following sub-sections.

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

The version of plugins and modules built into the images can be changed by providing
providing the Gerrit branch:
```
./build --branch stable-3.13
```

Note, if you do not include the `--gerrit-url` option, the image will automatically use
the Gerrit release that corresponds to the selected `--branch`.

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

The container images are meant to be used by the Kubernetes deployment provided
in this project. The images are thus not designed to be used in a standalone
setup. To run Gerrit on Docker use the
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
pipenv run pytest
```

Some tests will need to create files in a temporary directory. Some of these
files will be mounted into docker containers by tests. For this to work make
either sure that the system temporary directory is accessible by the Docker
daemon or set the base temporary directory to a directory accessible by Docker
by executing:

```sh
pipenv run pytest --basetemp=/tmp/k8sgerrit
```

By default the build of the container images will not use the build cache
created by docker. To enable the cache, execute:

```sh
pipenv run pytest --build-cache
```

Slow tests may be marked with the decorator `@pytest.mark.slow`. These tests
may then be skipped as follows:

```sh
pipenv run pytest --skip-slow
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
between outside clients and containers.

### slow

Marks tests that need an above average time to run.

### structure

Marks structure tests. These tests are meant to test, whether certain components
exist in a container. These tests ensure that components expected by the users
of the container are present in the containers.

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
