# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kubernetes deployment solution for Gerrit Code Review, providing container images, configurations, and a Kubernetes Operator for installing and operating Gerrit in Kubernetes clusters. The project consists of two main components:

1. **Container Images**: Docker images for various Gerrit components (gerrit, gerrit-init, git-gc, apache-git-http-backend, etc.)
2. **Gerrit Operator**: A Java-based Kubernetes operator that manages Gerrit deployments using Custom Resources

## Key Architecture Components

### Custom Resources (CRDs)
The operator manages several Custom Resources that define Gerrit deployments:
- **GerritCluster**: Main resource that manages one or multiple Gerrit instances with shared storage and networking
- **Gerrit**: Individual Gerrit instance (primary, replica, or receiver mode)
- **GitGarbageCollection**: Manages CronJobs for Git repository maintenance
- **Receiver**: Apache-based git-http-backend for receiving replication pushes
- **GerritNetwork**: Network components for ingress traffic routing
- **IncomingReplicationTask**: Fetches repositories from external git servers

### Ingress Support
The operator supports multiple ingress providers:
- **NONE**: No ingress, services only accessible via port-forwarding
- **INGRESS**: Kubernetes Ingress (requires Nginx Ingress Controller)
- **ISTIO**: Service mesh with advanced traffic routing
- **AMBASSADOR**: Edge Stack/Emissary Ingress support

### High Availability & Multi-site
- Supports scaling primary Gerrit with high-availability plugin
- Multi-site deployments with event replication via Kafka
- Global RefDB support (Zookeeper, Spanner) for shared metadata

## Common Development Commands

### Building Container Images
```bash
# Build all container images
./build

# Build specific images
./build gerrit git-gc

# Build with custom tag
./build --tag v1.0.0

# Build for different platform
./build --platform linux/arm64

# Build with custom Gerrit version
./build --gerrit-url https://example.com/gerrit.war
```

### Publishing Images
```bash
# Publish to registry
./publish <component-name>

# Publish with custom registry/org
./publish --registry docker.io --org myorg <component-name>

# Update latest tags
./publish --update-latest <component-name>
```

### Operator Development
```bash
# Build and test the operator (from operator/ directory)
cd operator
mvn clean install

# Build operator image
mvn clean install -Drevision=latest

# Run integration tests
mvn clean install -Pintegration-test

# Run specific test
mvn test -Dtest=GerritClusterE2E

# Format Java code
mvn fmt:format
```

### Testing
```bash
# Install Python test dependencies
pipenv install

# Run all tests
pipenv run pytest

# Run tests with build cache enabled
pipenv run pytest --build-cache

# Skip slow tests
pipenv run pytest --skip-slow

# Run specific test suite
pipenv run pytest tests/container-images/gerrit/

# Run specific test
pipenv run pytest tests/container-images/gerrit/test_container_build_gerrit.py::test_build_gerrit

# Run tests with specific marker
pipenv run pytest -m "docker"
```

### Python Code Quality
```bash
# Format Python code
pipenv run black $(find . -name '*.py')

# Lint Python code
pipenv run pylint $(find . -name '*.py')
```

### Helm Charts
```bash
# Install CRDs and operator using Helm
kubectl create ns gerrit-operator
helm dependency build helm-charts/gerrit-operator/
helm install gerrit-operator helm-charts/gerrit-operator/ -n gerrit-operator

# Update operator deployment
helm upgrade gerrit-operator helm-charts/gerrit-operator/ -n gerrit-operator
```

## Repository Structure

### `/container-images/`
Contains Dockerfiles and configurations for all container images:
- `base/`: Base image with common dependencies
- `gerrit-base/`: Gerrit-specific base image
- `gerrit/`: Main Gerrit application image
- `gerrit-init/`: Python-based initialization container
- `git-gc/`: Git garbage collection utilities
- `apache-git-http-backend/`: Apache-based git HTTP backend

### `/operator/`
Java-based Kubernetes operator source code:
- `src/main/java/`: Main operator implementation using Java Operator SDK
- `src/test/java/`: Unit and integration tests
- `pom.xml`: Maven configuration with dependencies and build plugins

### `/crd/`
Kubernetes Custom Resource Definitions:
- `current/`: Latest CRD versions
- `deprecated/`: Older CRD versions for backward compatibility

### `/helm-charts/`
Helm charts for deploying the operator:
- `gerrit-operator-crds/`: CRD installation chart
- `gerrit-operator/`: Main operator deployment chart

### `/Documentation/`
Comprehensive documentation including:
- `operator.md`: Detailed operator usage guide
- `operator-api-reference.md`: Complete API documentation
- `examples/`: Example CustomResource manifests

### `/tests/`
Python-based test suite using pytest:
- Container build and structure tests
- Integration tests for deployed containers
- Test fixtures and utilities

## Important Configuration Notes

### Restricted Gerrit Configuration Options
The operator automatically manages several gerrit.config options that cannot be overridden:
- `cache.directory`: Set to `cache` (in Gerrit site volume)
- `container.javaHome`: Set to Java installation path in container
- `container.replica`: Controlled by `spec.isReplica` in CustomResource
- `gerrit.basePath`: Set to `/var/gerrit/git`
- `gerrit.canonicalWebUrl`: Set based on ingress configuration
- `httpd.listenURL`: Set to `proxy-http://*:8080/` or `proxy-https://*:8080`
- `sshd.listenAddress`: Set automatically based on service configuration

### Feature Toggles
Set via environment variables in operator deployment:
- `CLUSTER_MODE`: `HIGH_AVAILABILITY` (default) or `MULTISITE`
- `INGRESS`: `NONE`, `INGRESS`, `ISTIO`, or `AMBASSADOR`

## Development Prerequisites

### Required Tools
- JDK 17
- Maven 3.6.3+
- Docker
- Python 3.11
- pipenv
- kubectl
- helm 3.0+
- yq (for YAML processing)

### For Testing/Development
- Minikube or similar Kubernetes cluster
- NFS provisioner for shared storage
- Ingress controller (if using INGRESS mode)

## Code Review Process

This project uses Gerrit for code review at https://gerrit-review.googlesource.com/. Install the commit-msg hook:

```bash
curl -Lo `git rev-parse --git-dir`/hooks/commit-msg \
    https://gerrit-review.googlesource.com/tools/hooks/commit-msg
chmod +x `git rev-parse --git-dir`/hooks/commit-msg
```

Push changes for review:
```bash
git push origin HEAD:refs/for/master
```

## Test Marks

- `@pytest.mark.docker`: Tests that start Docker containers
- `@pytest.mark.integration`: Integration tests between components
- `@pytest.mark.slow`: Tests requiring above-average time
- `@pytest.mark.structure`: Tests verifying container component structure
- `@pytest.mark.incremental`: Test classes requiring sequential execution