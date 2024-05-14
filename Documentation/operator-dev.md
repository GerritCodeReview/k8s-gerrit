# Operator Development

- [Operator Development](#operator-development)
  - [Prerequisites](#prerequisites)
  - [Bazel (Work In Progress)](#bazel-work-in-progress)
    - [Update dependencies](#update-dependencies)
    - [Build](#build)
    - [Test](#test)
    - [Publish](#publish)
  - [Maven](#maven)
    - [Build](#build-1)
    - [Test](#test-1)
    - [Publish](#publish-1)
  - [Versioning](#versioning)
  - [E2E Tests](#e2e-tests)

## Prerequisites

To build the Gerrit Operator, the following tools have to be installed:

- Java 17
- Maven\
  ***OR***
- [Bazel](https://github.com/bazelbuild/bazel)
  (ideally also [Bazelisk](https://github.com/bazelbuild/bazelisk))
- Docker

## Bazel (Work In Progress)

### Update dependencies

To update Maven dependencies, add the dependency to `MODULE.bazel`. Then
execute the following commands to lock the updated dependencies:

```sh
bazelisk mod deps --lockfile_mode=update
REPIN=1 bazelisk run @maven//:pin
bazelisk mod deps --lockfile_mode=update
```

### Build

To build the Gerrit Operator binary, run:

```sh
bazelisk build operator:operator-binary_deploy.jar
```

To directly build the docker image containing the Gerrit Operator binary, run:

```sh
bazelisk run operator:build
```

To generate the CRDs, run:

```sh
bazelisk build operator:crds
```

The CRDs can then be found in a `tar`-archive under `./bazel-bin/operator/crds.tar`.

Changes in CRDs should always be checked in to provide an easy way to install
them. Since Bazel will never update source files, this has to be done manually:

```sh
tar -xf bazel-bin/operator/crds.tar -C crd/current
operator/tools/combineCRDVersions
```

### Test

To run unit tests with Bazel, run:

```sh
bazelisk test operator:operator-tests
```

To run E2E tests with bazel, fill in the details in
`operator/src/test/resources/test.properties` and then run:

```sh
bazelisk test --config=e2e operator:operator-tests-e2e
```

### Publish

To publish the `gerrit-operator` container image, run:

```sh
bazelisk run operator:publish
```

By default the image will be pushed to `docker.io/k8sgerrit/gerrit-operator:latest`,
but this can be overridden:

```sh
bazelisk run operator:publish -- --repository $REPOSITORY --tag $TAG
```

## Maven

### Build

To build all components of the operator using Maven run:

```sh
cd operator
mvn clean install
```

This step compiles the Java source code into `.class` bytecode files in a newly
generated `operator/target` folder. A `gerrit-operator` image is also created
locally. Moreover, the CRD helm chart is updated with the latest CRDs as part of
this build step.

The jar-version and container image tag can be set using the `revision` property:

```sh
mvn clean install -Drevision=$(git describe --always --dirty)
```

### Test

Unit tests will be executed as part of the main build.

To run all E2E tests, use:

```sh
cd operator
mvn clean install -P integration-test -Dproperties=<path to properties file>
```

### Publish

To publish the container image of the Gerrit Operator:

1. Update the `docker.registry` and `docker.org` tags in the `operator/pom.xml`
file to point to your own Docker registry and org that you have permissions to
push to.

```xml
<docker.registry>my-registry</docker.registry>
<docker.org>my-org</docker.org>
```

2. run the following commands:

```sh
cd operator
mvn clean install -P publish
```

This will build the operator source code, create an image out of the
built artifacts, and publish this image to the registry specified in the
`pom.xml` file. The built image is multi-platform - it will run on both `amd64`
and `arm64` architectures. It is okay to run this build command from an ARM
Mac.

## Versioning

The Gerrit Operator manages a single group of CustomResourceDefinitions:
`gerritoperator.google.com`. Beginning from version `v1beta1` all
CustomResourceDefinitions in that group will be the same, even if only one of
those resource definitions changes compared to the previous version. This makes
it easier to track which versions are meant to work together.

Changes to CustomResourceDefinitions have to include a version update, otherwise
the change will be rejected during code review, since otherwise an update would
fail in the cluster.

The GerritOperator will only support two versions at a time. The new version is
always the one stored in ETCD. The older version has to be marked as deprecated
to indicate that it is not used by the reconcilers anymore.

To create a new version, the `operator/tools/newCRDVersion`-script can be used.
It will create a new version based on the old version in the operator code that
can be used to add the changes to the CRDs:

```sh
NEW_VERSION_NAME=v1beta5
operator/tools/newCRDVersion $NEW_VERSION_NAME
```

**Note:** On OSX, the `--osx` flag has to be used for the `newCRDVersion` script
since the `sed` version shipped with OSX uses different options than the GNU
version.

The commit adding a new version will be tagged in git using the version string.

## E2E Tests

Executing the E2E tests has a few infrastructure requirements that have to be
provided:

- An (unused) Kubernetes cluster
- The 'default' StorageClass that supports ReadWriteOnce access. It has to be
  possible to provision volumes using this StorageClass.
- A StorageClass that supports ReadWriteMany access. It has to be possible to
  provision volumes using this StorageClass. Such a StorageClass could be provided
  by the [NFS-subdir-provisioner chart](https://github.com/kubernetes-sigs/nfs-subdir-external-provisioner).
- An [Nginx Ingress Controller](https://github.com/kubernetes/ingress-nginx)
- An installation of [OpenLDAP](../supplements/test-cluster/ldap/openldap.yaml)
  with at least one user.
- Istio installed with the [profile](../istio/gerrit.profile.yaml) provided by
  this project
- A secret containing valid certificates for the given hostnames. For istio this
  secret has to be named `tls-secret` and be present in the `istio-system` namespace.
  For the Ingress controller, the secret has to be either set as the default
  secret to be used or somehow automatically be provided in the namespaces created
  by the tests and named `tls-secret`, e.g. by using Gardener to manage DNS and
  certificates.

A sample setup for components required in the cluster is provided under
`$REPO_ROOT/supplements/test-cluster`. Some configuration has to be done manually
(marked by `#TODO`) and the `deploy.sh`-script can be used to install/update all
components.

In addition, some properties have to be set to configure the tests:

- `rwmStorageClass`: Name of the StorageClass providing RWM-access (default:nfs-client)
- `registry`: Registry to pull container images from
- `RegistryOrg`: Organization of the container images
- `tag`: Container tag
- `registryUser`: User for the container registry
- `registryPwd`: Password for the container registry
- `ingressDomain`: Domain to be used for the ingress
- `istioDomain`: Domain to be used for istio
- `ldapAdminPwd`: Admin password for LDAP server
- `gerritUser`: Username of a user in LDAP
- `gerritPwd`: The password of `gerritUser`
