# Gerrit Operator

# Build

To build all components of the operator run:

```sh
mvn install jib:dockerBuild
```

# Deploy

First all CustomResourceDefinitions have to be deployed:

```sh
kubectl apply -f target/classes/META-INF/fabric8/*-v1.yml
```

or on clusters with Kubernetes versions < 1.16:

```sh
kubectl apply -f target/classes/META-INF/fabric8/*-v1beta1.yml
```

Then the operator and associated RBAC rules can be deployed:

```sh
kubectl apply -f k8s/operator.yaml
```

`k8s/operator.yaml` contains a basic deployment of the operator. Resources,
docker image name etc. might have to be adapted.

# Install custom resources

## Git-GC

An example of a GitGc-CustomResource can be found at `k8s/gitgc.sample.yaml`.
To install it into the cluster run:

```sh
kubectl apply -f k8s/gitgc.sample.yaml
```

The operator will create a CronJob based on the provided spec.
