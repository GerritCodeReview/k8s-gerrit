# Operator Development

1. [Operator Development](#operator-development)
   1. [Build](#build)
   2. [Versioning](#versioning)
   3. [Publish](#publish)
   4. [Tests](#tests)

## Build

For this step, you need Java 11 and Maven installed.

To build all components of the operator run:

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
operator/tools/newCRDVersion v1alpha v1beta1

# optionally delete the now obsolete version
operator/tools/newCRDVersion --delete v1alpha v1beta1 v1beta2
```

**Note:** On OSX, the `--osx` flag has to be used for the `newCRDVersion` script
since the `sed` version shipped with OSX uses different options than the GNU
version.

The commit adding a new version will be tagged in git using the version string.

## Publish

Currently, there does not exist a container image for the operator in the
`docker.io/k8sgerrit` registry. You must build your own image in order to run
the operator in your cluster. To publish the container image of the Gerrit
Operator:

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

## Tests

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

The properties should be set in the `test.properties` file. Alternatively, a
path of a properties file can be configured by using the
`-Dproperties=<path to properties file>`-option.

To run all E2E tests, use:

```sh
cd operator
mvn clean install -P integration-test -Dproperties=<path to properties file>
```

Note, that running the E2E tests will also involve pushing the container image
to the repository configured in the properties file.
