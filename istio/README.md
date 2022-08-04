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