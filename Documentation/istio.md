# Istio

Istio provides an alternative way to control ingress traffic into the cluster.
In addition, it allows to finetune the traffic inside the cluster and provides
a huge repertoire of load balancing and routing mechanisms.

***note
Currently, only the Gerrit replica chart allows using istio out of the box.
***

## Dependencies

- istioctl \
  To install follow these
  [instructions](https://istio.io/docs/ops/diagnostic-tools/istioctl/#install-hahahugoshortcode-s2-hbhb)

## Install istio

An example configuration based on the default profile provided by istio can be
found under `./istio/gerrit.profile.yaml`. To install istio with this profile,
run:

```sh
istioctl manifest apply -f istio/gerrit.profile.yaml
```

To install Gerrit using istio for networking, the namespace running Gerrit has to
be configured to enable sidecar injection, by setting the `istio-injection: enabled`
label. An example for such a namespace can be found at `./istio/namespace.yaml`.

To be able to use Kiali, credentials have to be provided. A secret for doing so,
can be found at `./istio/kiali.secret.yaml`. Adapt the credentials and apply them:

```sh
kubectl apply -f ./istio/kiali.secret.yaml
```

## Uninstall istio

To uninstall istio, run:

```sh
istioctl manifest generate -f istio/gerrit.profile.yaml > istio/gerrit.manifest.yaml
kubectl delete -f istio/gerrit.manifest.yaml
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
