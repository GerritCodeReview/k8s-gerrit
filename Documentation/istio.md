# Istio

Istio provides an alternative way to control ingress traffic into the cluster.
In addition, it allows to finetune the traffic inside the cluster and provides
a huge repertoire of load balancing and routing mechanisms.

***note
Currently, only the Gerrit replica chart allows using istio out of the box.
***

## Install istio

An example configuration based on the default profile provided by istio can be
found under `./istio/src/`. Some values will have to be adapted to the respective
system. These are marked by comments tagged with `TO_BE_CHANGED`.
To install istio with this configuration, run:

```sh
kubectl apply -f istio/istio-system-namespace.yaml
istioctl install -f istio/gerrit.profile.yaml
```

To install Gerrit using istio for networking, the namespace running Gerrit has to
be configured to enable sidecar injection, by setting the `istio-injection: enabled`
label. An example for such a namespace can be found at `./istio/namespace.yaml`.

## Uninstall istio

To uninstall istio, run:

```sh
istioctl uninstall -f istio/gerrit.profile.yaml
```

## Restricting access to a list of allowed IPs

In development setups, it might be wanted to allow access to the setup only from
specified IPs. This can be done by patching the `spec.loadBalancerSourceRanges`
value of the service used for the IngressGateway. A patch doing that can be
uncommented in `istio/gerrit.profile.yaml`.
