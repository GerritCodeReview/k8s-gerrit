# Roadmap

## General

### Planned features

- **Automated verification process**: Run tests automatically to verify changes. \
  \
  Most tests in the project require a Kubernetes cluster and some additional
  prerequisites, e.g. istio. Currently, the Gerrit OpenSOurce community does not
  have these resources. At SAP, we plan to run verification in our internal systems,
  which won't be publicly viewable, but could already vote. Builds would only
  be triggered, if a maintainer votes `+1` on the `Build-Approved`-label. \
  \
  Builds can be moved to a public CI at a later point in time.

- **Automated publishing of container images**: Publishing container images will
  happen automatically on ref-updated using a CI.

- **Support for multiple Gerrit versions**: All currently supported Gerrit versions
  will also be supported in k8s-gerrit. \
  \
  Currently, container images used by this project are only published for a single
  Gerrit version, which is updated on an irregular schedule. Introducing stable
  branches for each gerrit version will allow to maintain container images for
  multiple Gerrit versions. Gerrit binaries will be updated with each official
  release and more frequently on `master`. This will be (at least partially)
  automated.

## Gerrit Operator

### Version 1.0

#### Planned features

- **Versioning of CRDs**: Provide migration paths between API changes in CRDs. \
  \
  At the moment updates to the CRD are done without providing a migration path.
  This means a complete reinstallation of CRDS, Operator, CRs and dependent resources
  is required. This is not acceptable in a productive environment. Thus,
  the operator will always support the last two versions of each CRD, if applicable,
  and provide a migration path between those versions.

- **High-availability**: Primary Gerrit StatefulSets will have limited support for
  horizontal scaling. \
  \
  Scaling will be enabled using the [high-availability plugin](https://gerrit.googlesource.com/plugins/high-availability/).
  Primary Gerrits will run in Active/Active configuration. Support of two primary
  Gerrit instances, i.e. 2 pods in a StatefulSet, is the aim, but more instances
  might be possible.

- **Global RefDB support**: Global RefDB will add support for Active/Active
  configurations of multiple primary Gerrits. \
  \
  The [Global RefDB](https://gerrit.googlesource.com/modules/global-refdb) support
  is required for high-availability as described in the previous point. The
  Gerrit Operator will automatically set up Gerrit to use a Global RefDB
  implementation. Support for following implementations is planned:
  - [spanner-refdb](https://gerrit.googlesource.com/plugins/spanner-refdb) (still in development)
  - [zookeeper-refdb](https://gerrit.googlesource.com/plugins/zookeeper-refdb)

  \
  The Gerrit Operator will not set up the database used for the Global RefDB. It
  will however manage plugin/module installation and configuration in Gerrit and
  service mesh integration (might differ depending on ingress/service mesh provider).

- **Log collection**: Support addition of sidecar running a log collection agent
  to send logs of all components to some logging stack. \
  \
  Planned supported log collectors:
  - [OpenTelemetry agent](https://opentelemetry.io/docs/collector/deployment/agent/)
  - Option to add a custom sidecar

- **Automated reload of plugins**: Reload plugins on configuration change. \
  \
  Configuration changes in plugins typically don't require a restart of Gerrit,
  but just to reload the plugin. To avoid unnecessary downtime of pods, the
  Gerrit Operator will only reload affected plugins and not restart all pods, if
  only the plugin's configuration changed.

#### Potential features

- **Support for additional Ingress controllers**: Add support for setting up routing
  configurations for additional Ingress controllers \
  \
  Additional ingress controllers might include:
  - [Ambassador](https://www.getambassador.io/products/edge-stack/api-gateway)
  - Full support for [Nginx](https://docs.nginx.com/nginx-ingress-controller/)

- **Support for additional log collection agents**: \
  \
  Additional log collection agents might include:
  - fluentbit
  - Option to add a custom sidecar

## Helm charts

Only limited support is planned for the `gerrit` and `gerrit-replica` helm-charts
as soon as the Gerrit Operator reaches version 1.0. The reason is that the double
maintenance of all features would not be feasible with the current number of
contributors. The Gerrit Operator will support all features that are provided by
the helm charts. If community members would like to adopt maintainership of the
helm-charts, this would be very much appreciated and the helm-charts could then
continued to be supported.
