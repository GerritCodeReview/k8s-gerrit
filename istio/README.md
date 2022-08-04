# Istio for Gerrit

## Configuring istio

It is recommended to set a static IP to be used by the LoadBalancer service
deployed by istio. To do that set
`spec.components.ingressGateways[0].k8s.overlays[0].patches[0].value`, which is
commented out by default, which causes the use of an ephemeral IP.

## Installing istio

Create the `istio-system`-namespace:

```sh
kubectl apply -f ./istio/istio-system-namespace.yaml
```

Verify that your istioctl version (`istioctl version`) matches the version in
`istio/gerrit.profile.yaml` under `spec.tag`.

Install istio:

```sh
istioctl install -f istio/gerrit.profile.yaml
```

## Update from 1.11.4 to 1.14.1

1. Tags has been updated to 1.14.1 from 1.11.4.

    ```
    tag: 1.14.1
    ```

2. For resource CPU, values for target has been refactored to following format in paths:

    spec/components/egressGateways/0/k8s/hpaSpec/metrics/0/resource
    spec/components/ingressGateways/0/k8s/hpaSpec/metrics/0/resource


    ```
    name: cpu
    target:
        type: Utilization
        averageUtilization: 80
    ```