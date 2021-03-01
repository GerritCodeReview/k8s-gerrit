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
kubectl apply -f istio/src
```

To install Gerrit using istio for networking, the namespace running Gerrit has to
be configured to enable sidecar injection, by setting the `istio-injection: enabled`
label. An example for such a namespace can be found at `./istio/namespace.yaml`.
## Uninstall istio

To uninstall istio, run:

```sh
kubectl delete -f istio/src
```

## Restricting access to a list of allowed IPs

In development setups, it might be wanted to allow access to the setup only from
specified IPs. This can usually be done using an AuthorizationPolicy. On AWS this
does not work, since the load balancer hides the original IP. However, the
istio-ingressgateway can be patched to enable access only from a given range of
IPs. To do this, use the following command:

```sh
kubectl patch service istio-ingressgateway -n istio-system -p '{"spec":{"loadBalancerSourceRanges":["1.2.3.4"]}}'
```
