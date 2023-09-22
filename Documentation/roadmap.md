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

- **Integration test suite**: A test suite that can be used to test a GerritCluster. \
  \
  A GerritCluster running in a Kubernetes cluster consists of multiple components.
  Having a suite of automated tests would greatly help to verify deployments in
  development landscapes before going productive.

## Gerrit Operator

### Version 1.0

#### Implemented features

- **High-availability**: Primary Gerrit StatefulSets will have limited support for
  horizontal scaling. \
  \
  Scaling has been enabled using the [high-availability plugin](https://gerrit.googlesource.com/plugins/high-availability/).
  Primary Gerrits will run in Active/Active configuration. Currently, two primary
  Gerrit instances, i.e. 2 pods in a StatefulSet, are supported

- **Global RefDB support**: Global RefDB is required for Active/Active configurations
  of multiple primary Gerrits. \
  \
  The [Global RefDB](https://gerrit.googlesource.com/modules/global-refdb) support
  is required for high-availability as described in the previous point. The
  Gerrit Operator automatically sets up Gerrit to use a Global RefDB
  implementation. The following implementations are supported:
  - [spanner-refdb](https://gerrit.googlesource.com/plugins/spanner-refdb)
  - [zookeeper-refdb](https://gerrit.googlesource.com/plugins/zookeeper-refdb)

  \
  The Gerrit Operator does not set up the database used for the Global RefDB. It
  does however manage plugin/module installation and configuration in Gerrit.

- **Full support for Nginx**: The integration of Ingresses managed by the Nginx
  ingress controller now supports automated routing. \
  \
  Instead of requiring users to use different subdomains for the different Gerrit
  deployments in the GerritCluster, requests are now automatically routed to the
  respective deployments. SSH has still to be set up manually, since this requires
  setting up the routing in the Nginx ingress controller itself.

#### Planned features

- **Versioning of CRDs**: Provide migration paths between API changes in CRDs. \
  \
  At the moment updates to the CRD are done without providing a migration path.
  This means a complete reinstallation of CRDS, Operator, CRs and dependent resources
  is required. This is not acceptable in a productive environment. Thus,
  the operator will always support the last two versions of each CRD, if applicable,
  and provide a migration path between those versions.

- **Log collection**: Support addition of sidecar running a log collection agent
  to send logs of all components to some logging stack. \
  \
  Planned supported log collectors:
  - [OpenTelemetry agent](https://opentelemetry.io/docs/collector/deployment/agent/)
  - Option to add a custom sidecar

- **Support for additional Ingress controllers**: Add support for setting up routing
  configurations for additional Ingress controllers \
  \
  Additional ingress controllers might include:
  - [Ambassador](https://www.getambassador.io/products/edge-stack/api-gateway)

### Version 1.x

#### Potential features

- **Support for additional log collection agents**: \
  \
  Additional log collection agents might include:
  - fluentbit
  - Option to add a custom sidecar

- **Additional ValidationWebhooks**: Proactively avoid unsupported configurations. \
  \
  ValidationWebhooks are already used to avoid accepting unsupported configurations,
  e.g. deploying more than one primary Gerrit CustomResource per GerritCluster.
  So far not all such cases are covered. Thus, the set of validations will be
  further expanded.

- **Better test coverage**: More tests are required to find bugs earlier.

- **Automated reload of plugins**: Reload plugins on configuration change. \
  \
  Configuration changes in plugins typically don't require a restart of Gerrit,
  but just to reload the plugin. To avoid unnecessary downtime of pods, the
  Gerrit Operator will only reload affected plugins and not restart all pods, if
  only the plugin's configuration changed.

- **Externalized (re-)indexing**: Alleviate load caused by online reindexing. \
  \
  On large Gerrit sites online reindexing due to schema migrations `a)` or initialization `b)`
  of a new site might take up to weeks and use a lot of resources, which might
  cause performance issues. This is not acceptable in production. The current
  plan to solve this issue is to implement a separate Gerrit deployment (GerritIndexer)
  that is not exposed to clients and that takes over the task of online reindexing.
  The GerritIndexer will mount the same repositories and will share events via
  the high-availability plugin. However, it will access repositories in read-only
  mode. \
  This solves the above named scenarios as follows: \
  \
  a) **Schema migrations**: If a Gerrit update including a schema migration for
    an index is applied, the Gerrit instances serving clients will be configured
    to continue to use the old schema. Online reindexing will be disabled in
    those instances. The GerritIndexer will have online reindexing enabled and
    will start to build the new index version. As soon as it is finished, i.e.
    it could start to use the new index version as read index, it will make a
    copy of the new index and publish it, e.g. using a shared filesystem. A
    restart of the Gerrit instances serving other clients will be triggered.
    During this restart the new index will be copied into the site. Since there
    may have been updated index entries since the new index version was published
    indexing of entries updated in the meantime will be triggered. \
  \
  b) **Initialization of a new site**: If Gerrit is horizontally scaled, it will
    be started with an empty index, i.e. it has to build the complete index. To
    avoid this, the GerritIndexer deployment will continuously keep a copy of the
    indexes up-to-date. It will regularly be stopped and a copy of the index will
    be stored in a shared volume. This can be used as a base for new instances, which
    then only have to update index entries that were changed in the meantime.

- **Autoscaling**: Automatically scale Gerrit deployments based on usage. \
  \
  Metrics like available workers in the thread pools could be used to decide to
  scale the Gerrit deployment horizontally. This would allow to dynamically adapt
  to the current load. This helps to save costs and resources.

### Version 2.0

#### Potential features

- **Multi region support**: Support setups that are distributed over multiple regions. \
  \
  Supporting Gerrit installations that are distributed over multiple regions would
  allow to serve clients all over the world without large differences in latency
  and would also improve availability and reduce the risks of data loss. \
  Such a setup could be achieved by using the [multi-site setup](https://gerrit.googlesource.com/plugins/multi-site/).

- **Remove the dependency on shared storage**: Use completely independent sites
  instead of sharing a filesystem for some site components. \
  \
  NFS and other shared filesystems potentially might cause performance issues on
  larger Gerrit installations due to latencies. A potential solution might be
  to use the [multi-site setup](https://gerrit.googlesource.com/plugins/multi-site/)
  to separate the sites of all instances and to use events and replication to
  share the state

- **Shared index**: Using an external centralized index, e.g. OpenSearch instead
  of x copies of a Lucene index. \
  \
  Maintaining x copies of an index, where x is the number of Gerrit instances in
  a gerritCluster, is unnecessarily expensive, since the same write transactions
  have to be potentially done x times. Using a single centralized index would
  resolve this issue.

- **Shared cache**: Using an external centralized cache for all Gerrit instances. \
  \
  Using a single cache for all Gerrit instances will reduce the number of
  computations for each Gerrit instance, since not every instance will have to
  keep its own copy up-to-date.

- **Sharding**: Shard a site based on repositories. \
  \
  Repositories served by a single GerritCluster might be quite diverse, e.g. ranging
  from a few kilobytes to several gigabytes or repositories seeing high traffic
  and other barely being fetched. It is not trivial to configure Gerrit to work
  optimally for all repositories. Being able to shard at least the Gerrit Replicas
  would help to optimally serve all repositories.

## Helm charts

Only limited support is planned for the `gerrit` and `gerrit-replica` helm-charts
as soon as the Gerrit Operator reaches version 1.0. The reason is that the double
maintenance of all features would not be feasible with the current number of
contributors. The Gerrit Operator will support all features that are provided by
the helm charts. If community members would like to adopt maintainership of the
helm-charts, this would be very much appreciated and the helm-charts could then
continued to be supported.
