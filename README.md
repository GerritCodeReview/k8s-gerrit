# Docker images for running a Gerrit Slave Setup

Images to run a Gerrit slave setup based on the latest production-2.12 Gerrit build.

# Building image

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

# Publishing images

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

# Running image

To configure the registry and image version, the respective values can be
configured via env varaibles `REGISTRY` and `TAG`. In addition, these values can
also be passed as command line options named `--registry` and `--tag` in which
case they override the values from env variables.

Assuming a Gerrit site already exists, is located at `/path/to/gerrit-slave` and
owned by the `gerrit`-user defined in the docker image (default `UID: 1000`) run
the following command for each image in the directories containing the respective
docker image:

```
./start /path/to/gerrit-slave <component-name>
```

The `<component-name>` is one of: `apache-git-http-backend`, `git-gc`,
`gerrit-slave`.

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

# Important notes

Currently, java is installed under `/usr/lib/jvm/java-8-openjdk-amd64/jre`.
Therefore, make sure that `container.javaHome` is set to that path in the `gerrit.config`:
```
  javaHome = /usr/lib/jvm/java-8-openjdk-amd64/jre
```

The mysql-replication-init docker image is only required for setting up the Gerrit
slave on Kubernetes. If deploying the Gerrit slave outside of Kubernetes, it can
be ignored.
