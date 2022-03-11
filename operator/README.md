# Gerrit Operator

## Build

To build all components of the operator run:

```sh
# With E2E tests
mvn clean install jib:dockerBuild
# Without E2E tests
mvn clean install -DskipTests jib:dockerBuild
```

## Deploy

First all CustomResourceDefinitions have to be deployed:

```sh
kubectl apply -f target/classes/META-INF/fabric8/*-v1.yml
```

Then the operator and associated RBAC rules can be deployed:

```sh
kubectl apply -f k8s/operator.yaml
```

`k8s/operator.yaml` contains a basic deployment of the operator. Resources,
docker image name etc. might have to be adapted.

## Install custom resources

### GitGabageCollection

An example of a GitGc-CustomResource can be found at `k8s/gitgc.sample.yaml`.
To install it into the cluster run:

```sh
kubectl apply -f k8s/gitgc.sample.yaml
```

The operator will create a CronJob based on the provided spec.

## Configure custom resources

### GitGarbageCollection

```yaml
apiVersion: "gerritoperator.google.com/v1alpha1"
kind: GitGarbageCollection
metadata:
  name: gitgc
spec:
  # Container image containing the git gc script. Expected to be the one maintained
  # by the k8sgerrit project (default: k8sgerrit/git-gc)
  image: k8sgerrit/git-gc
  # Cron schedule defining when to run git gc (mandatory)
  schedule: "*/5 * * * *"
  # Resource requests/limits of the git gc container
  # (https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
  # (optional)
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 100m
      memory: 256Mi
  # Name of an existing PVC that will be used to store the logs (mandatory)
  logPVC: logs-pvc
  # Name of an existing PVC that claims the volume containing the git repositories
  # to be gc'ed (mandatory)
  repositoryPVC: git-repositories-pvc
```
